package com.gentics.cailun.core.verticle;

import static com.gentics.cailun.demo.DemoDataProvider.PROJECT_NAME;
import static io.vertx.core.http.HttpMethod.DELETE;
import static io.vertx.core.http.HttpMethod.GET;
import static io.vertx.core.http.HttpMethod.PUT;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import io.vertx.core.http.HttpMethod;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gentics.cailun.core.AbstractRestVerticle;
import com.gentics.cailun.core.data.model.Tag;
import com.gentics.cailun.core.data.model.auth.PermissionType;
import com.gentics.cailun.core.data.service.TagService;
import com.gentics.cailun.core.rest.tag.request.TagUpdateRequest;
import com.gentics.cailun.core.rest.tag.response.TagListResponse;
import com.gentics.cailun.core.rest.tag.response.TagResponse;
import com.gentics.cailun.test.AbstractRestVerticleTest;
import com.gentics.cailun.util.JsonUtils;

public class TagVerticleTest extends AbstractRestVerticleTest {

	@Autowired
	private TagVerticle tagVerticle;

	@Autowired
	private TagService tagService;

	@Override
	public AbstractRestVerticle getVerticle() {
		return tagVerticle;
	}

	@Test
	public void testReadAllTags() throws Exception {
		roleService.addPermission(info.getRole(), data().getNews(), PermissionType.READ);

		final int nTags = 142;
		for (int i = 0; i < nTags; i++) {
			Tag extraTag = new Tag();
			extraTag.setCreator(info.getUser());
			extraTag = tagService.save(extraTag);
			roleService.addPermission(info.getRole(), extraTag, PermissionType.READ);
		}

		// Don't grant permissions to the no perm tag. We want to make sure that this one will not be listed.
		Tag noPermTag = new Tag();
		noPermTag.setCreator(info.getUser());
		noPermTag = tagService.save(noPermTag);
		noPermTag = tagService.reload(noPermTag);
		assertNotNull(noPermTag.getUuid());

		// Test default paging parameters
		String response = request(info, HttpMethod.GET, "/api/v1/" + PROJECT_NAME + "/tags/", 200, "OK");
		TagListResponse restResponse = JsonUtils.readValue(response, TagListResponse.class);
		Assert.assertEquals(25, restResponse.getMetainfo().getPerPage());
		Assert.assertEquals(0, restResponse.getMetainfo().getCurrentPage());
		Assert.assertEquals(15, restResponse.getData().size());

		int perPage = 11;
		response = request(info, HttpMethod.GET, "/api/v1/" + PROJECT_NAME + "/tags/?per_page=" + perPage + "&page=" + 3, 200, "OK");
		restResponse = JsonUtils.readValue(response, TagListResponse.class);
		Assert.assertEquals(perPage, restResponse.getMetainfo().getPerPage());
		Assert.assertEquals(perPage, restResponse.getData().size());

		// Extra Tags + permitted tag
		int totalTags = nTags + data().getTotalTags();
		int totalPages = (int) Math.ceil(totalTags / (double) perPage);
		Assert.assertEquals("The response did not contain the correct amount of items", perPage, restResponse.getData().size());
		Assert.assertEquals(3, restResponse.getMetainfo().getCurrentPage());
		Assert.assertEquals("The amount of total pages did not match the expected value. There are {" + totalTags + "} tags and {" + perPage
				+ "} tags per page", totalPages, restResponse.getMetainfo().getPageCount());
		Assert.assertEquals((int) perPage, restResponse.getMetainfo().getPerPage());
		Assert.assertEquals("The total tag count does not match.", totalTags, restResponse.getMetainfo().getTotalCount());

		List<TagResponse> allTags = new ArrayList<>();
		for (int page = 0; page < totalPages; page++) {
			response = request(info, HttpMethod.GET, "/api/v1/" + PROJECT_NAME + "/tags/?per_page=" + perPage + "&page=" + page, 200, "OK");
			restResponse = JsonUtils.readValue(response, TagListResponse.class);
			allTags.addAll(restResponse.getData());
		}
		Assert.assertEquals("Somehow not all users were loaded when loading all pages.", totalTags, allTags.size());

		// Verify that the no_perm_tag is not part of the response
		final String noPermTagUUID = noPermTag.getUuid();
		List<TagResponse> filteredUserList = allTags.parallelStream().filter(restTag -> restTag.getUuid().equals(noPermTagUUID))
				.collect(Collectors.toList());
		assertTrue("The no perm tag should not be part of the list since no permissions were added.", filteredUserList.size() == 0);

		response = request(info, HttpMethod.GET, "/api/v1/" + PROJECT_NAME + "/tags/?per_page=" + perPage + "&page=" + -1, 400, "Bad Request");
		expectMessageResponse("error_invalid_paging_parameters", response);
		response = request(info, HttpMethod.GET, "/api/v1/" + PROJECT_NAME + "/tags/?per_page=" + 0 + "&page=" + 1, 400, "Bad Request");
		expectMessageResponse("error_invalid_paging_parameters", response);
		response = request(info, HttpMethod.GET, "/api/v1/" + PROJECT_NAME + "/tags/?per_page=" + -1 + "&page=" + 1, 400, "Bad Request");
		expectMessageResponse("error_invalid_paging_parameters", response);

		perPage = 25;
		totalPages = (int) Math.ceil(totalTags / (double) perPage);
		response = request(info, HttpMethod.GET, "/api/v1/" + PROJECT_NAME + "/tags/?per_page=" + perPage + "&page=" + 4242, 200, "OK");
		String json = "{\"data\":[],\"_metainfo\":{\"page\":4242,\"per_page\":25,\"page_count\":" + totalPages + ",\"total_count\":" + totalTags
				+ "}}";
		assertEqualsSanitizedJson("The json did not match the expected one.", json, response);
	}

	@Test
	public void testReadTagByUUID() throws Exception {

		Tag tag = data().getNews();
		assertNotNull("The UUID of the tag must not be null.", tag.getUuid());

		roleService.addPermission(info.getRole(), tag, PermissionType.READ);

		String response = request(info, GET, "/api/v1/" + PROJECT_NAME + "/tags/" + tag.getUuid(), 200, "OK");
		String json = "{\"uuid\":\"uuid-value\",\"schemaName\":\"tag\",\"order\":0,\"creator\":{\"uuid\":\"uuid-value\",\"lastname\":\"Doe\",\"firstname\":\"Joe\",\"username\":\"joe1\",\"emailAddress\":\"j.doe@spam.gentics.com\",\"groups\":[\"joe1_group\"],\"perms\":[]},\"properties\":{},\"childTags\":[],\"perms\":[\"read\",\"create\",\"update\",\"delete\"]}";
		assertEqualsSanitizedJson("Response json does not match the expected one.", json, response);
	}

	@Test
	public void testReadTagByUUIDWithDepthParam() throws Exception {

		Tag tag = data().getNews();
		assertNotNull("The UUID of the tag must not be null.", tag.getUuid());

		roleService.addPermission(info.getRole(), tag, PermissionType.READ);

		String response = request(info, GET, "/api/v1/" + PROJECT_NAME + "/tags/" + tag.getUuid() + "?depth=2&lang=de,en", 200, "OK");
		String json = "{\"uuid\":\"uuid-value\",\"schemaName\":\"tag\",\"order\":0,\"creator\":{\"uuid\":\"uuid-value\",\"lastname\":\"Doe\",\"firstname\":\"Joe\",\"username\":\"joe1\",\"emailAddress\":\"j.doe@spam.gentics.com\",\"groups\":[\"joe1_group\"],\"perms\":[]},\"properties\":{\"de\":{\"name\":\"Neuigkeiten\"},\"en\":{\"name\":\"News\"}},\"childTags\":[{\"uuid\":\"uuid-value\",\"schemaName\":\"tag\",\"order\":0,\"creator\":{\"uuid\":\"uuid-value\",\"lastname\":\"Doe\",\"firstname\":\"Joe\",\"username\":\"joe1\",\"emailAddress\":\"j.doe@spam.gentics.com\",\"groups\":[\"joe1_group\"],\"perms\":[]},\"properties\":{\"en\":{\"name\":\"2014\"}},\"childTags\":[{\"uuid\":\"uuid-value\",\"schemaName\":\"tag\",\"order\":0,\"creator\":{\"uuid\":\"uuid-value\",\"lastname\":\"Doe\",\"firstname\":\"Joe\",\"username\":\"joe1\",\"emailAddress\":\"j.doe@spam.gentics.com\",\"groups\":[\"joe1_group\"],\"perms\":[]},\"properties\":{\"en\":{\"name\":\"2015\"}},\"childTags\":[],\"perms\":[\"read\",\"create\",\"update\",\"delete\"]}],\"perms\":[\"read\",\"create\",\"update\",\"delete\"]}],\"perms\":[\"read\",\"create\",\"update\",\"delete\"]}";
		assertEqualsSanitizedJson("Response json does not match the expected one.", json, response);
	}

	@Test
	public void testReadTagByUUIDWithSingleLanguage() throws Exception {

		Tag tag = data().getNews();
		assertNotNull("The UUID of the tag must not be null.", tag.getUuid());

		roleService.addPermission(info.getRole(), tag, PermissionType.READ);

		String response = request(info, GET, "/api/v1/" + PROJECT_NAME + "/tags/" + tag.getUuid() + "?lang=en", 200, "OK");
		String json = "{\"uuid\":\"uuid-value\",\"schemaName\":\"tag\",\"order\":0,\"creator\":{\"uuid\":\"uuid-value\",\"lastname\":\"Doe\",\"firstname\":\"Joe\",\"username\":\"joe1\",\"emailAddress\":\"j.doe@spam.gentics.com\",\"groups\":[\"joe1_group\"],\"perms\":[]},\"properties\":{\"en\":{\"name\":\"News\"}},\"childTags\":[],\"perms\":[\"read\",\"create\",\"update\",\"delete\"]}";
		assertEqualsSanitizedJson("Response json does not match the expected one.", json, response);
	}

	@Test
	public void testReadTagByUUIDWithMultipleLanguages() throws Exception {

		Tag tag = data().getNews();
		assertNotNull("The UUID of the tag must not be null.", tag.getUuid());

		roleService.addPermission(info.getRole(), tag, PermissionType.READ);

		String response = request(info, GET, "/api/v1/" + PROJECT_NAME + "/tags/" + tag.getUuid() + "?lang=en,de", 200, "OK");
		String json = "{\"uuid\":\"uuid-value\",\"schemaName\":\"tag\",\"order\":0,\"creator\":{\"uuid\":\"uuid-value\",\"lastname\":\"Doe\",\"firstname\":\"Joe\",\"username\":\"joe1\",\"emailAddress\":\"j.doe@spam.gentics.com\",\"groups\":[\"joe1_group\"],\"perms\":[]},\"properties\":{\"de\":{\"name\":\"Neuigkeiten\"},\"en\":{\"name\":\"News\"}},\"childTags\":[],\"perms\":[\"read\",\"create\",\"update\",\"delete\"]}";
		assertEqualsSanitizedJson("Response json does not match the expected one.", json, response);
	}

	@Test
	public void testReadTagByUUIDWithoutPerm() throws Exception {

		Tag tag = data().getNews();
		assertNotNull("The UUID of the tag must not be null.", tag.getUuid());

		roleService.revokePermission(info.getRole(), tag, PermissionType.READ);

		String response = request(info, GET, "/api/v1/" + PROJECT_NAME + "/tags/" + tag.getUuid(), 403, "Forbidden");
		expectMessageResponse("error_missing_perm", response, tag.getUuid());
	}

	@Test
	public void testUpdateTagByUUID() throws Exception {
		Tag tag = data().getNews();

		roleService.addPermission(info.getRole(), tag, PermissionType.UPDATE);
		roleService.addPermission(info.getRole(), tag, PermissionType.READ);

		// Create an tag update request
		TagUpdateRequest request = new TagUpdateRequest();
		request.setUuid(tag.getUuid());
		request.addProperty("en", "name", "new Name");

		// 1. Read the current tag in english
		String response = request(info, GET, "/api/v1/" + PROJECT_NAME + "/tags/" + tag.getUuid() + "?lang=en", 200, "OK");
		TagResponse tagResponse = JsonUtils.readValue(response, TagResponse.class);
		Assert.assertEquals(tag.getName(data().getEnglish()), tagResponse.getProperty("en", "name"));

		// 2. Setup the request object
		TagUpdateRequest tagUpdateRequest = new TagUpdateRequest();
		final String newName = "new Name";
		tagUpdateRequest.addProperty("en", "name", newName);
		Assert.assertEquals(newName, tagUpdateRequest.getProperty("en", "name"));

		// 3. Send the request to the server
		// TODO test with no ?lang query parameter
		String requestJson = JsonUtils.toJson(request);
		response = request(info, PUT, "/api/v1/" + PROJECT_NAME + "/tags/" + tag.getUuid() + "?lang=en", 200, "OK", requestJson);
		String json = "{\"uuid\":\"uuid-value\",\"schemaName\":\"tag\",\"order\":0,\"creator\":{\"uuid\":\"uuid-value\",\"lastname\":\"Doe\",\"firstname\":\"Joe\",\"username\":\"joe1\",\"emailAddress\":\"j.doe@spam.gentics.com\",\"groups\":[\"joe1_group\"],\"perms\":[]},\"properties\":{\"en\":{\"name\":\"new Name\"}},\"childTags\":[],\"perms\":[\"read\",\"create\",\"update\",\"delete\"]}";
		assertEqualsSanitizedJson("Response json does not match the expected one.", json, response);

		// 4. read the tag again and verify that it was changed
		response = request(info, GET, "/api/v1/" + PROJECT_NAME + "/tags/" + tag.getUuid() + "?lang=en", 200, "OK");
		tagResponse = JsonUtils.readValue(response, TagResponse.class);
		Assert.assertEquals(request.getProperty("en", "name"), tagResponse.getProperty("en", "name"));

		// TODO verify that only that property was changed
	}

	@Test
	public void testUpdateTagByUUIDWithoutPerm() throws Exception {
		Tag tag = data().getNews();

		roleService.revokePermission(info.getRole(), tag, PermissionType.UPDATE);
		roleService.addPermission(info.getRole(), tag, PermissionType.READ);

		// Create an tag update request
		TagUpdateRequest request = new TagUpdateRequest();
		request.setUuid(tag.getUuid());
		request.addProperty("en", "name", "new Name");

		TagResponse updateTagResponse = new TagResponse();
		updateTagResponse.addProperty("english", "name", "new Name");

		String requestJson = new ObjectMapper().writeValueAsString(request);
		String response = request(info, PUT, "/api/v1/" + PROJECT_NAME + "/tags/" + tag.getUuid(), 403, "Forbidden", requestJson);
		expectMessageResponse("error_missing_perm", response, tag.getUuid());

		// read the tag again and verify that it was not changed
		response = request(info, GET, "/api/v1/" + PROJECT_NAME + "/tags/" + tag.getUuid() + "?lang=en", 200, "OK");
		TagResponse tagUpdateRequest = JsonUtils.readValue(response, TagResponse.class);
		Assert.assertEquals(tag.getName(data().getEnglish()), tagUpdateRequest.getProperty("en", "name"));
	}

	// Delete Tests
	@Test
	public void testDeleteTagByUUID() throws Exception {

		roleService.addPermission(info.getRole(), data().getNews(), PermissionType.DELETE);

		String response = request(info, DELETE, "/api/v1/" + PROJECT_NAME + "/tags/" + data().getNews().getUuid(), 200, "OK");
		expectMessageResponse("tag_deleted", response, data().getNews().getUuid());
		assertNull("The tag should have been deleted", tagService.findByUUID(data().getNews().getUuid()));
	}

	@Test
	public void testDeleteTagByUUIDWithoutPerm() throws Exception {

		roleService.revokePermission(info.getRole(), data().getNews(), PermissionType.DELETE);

		String response = request(info, DELETE, "/api/v1/" + PROJECT_NAME + "/tags/" + data().getNews().getUuid(), 403, "Forbidden");
		expectMessageResponse("error_missing_perm", response, data().getNews().getUuid());
		assertNotNull("The tag should not have been deleted", tagService.findByUUID(data().getNews().getUuid()));
	}

}
