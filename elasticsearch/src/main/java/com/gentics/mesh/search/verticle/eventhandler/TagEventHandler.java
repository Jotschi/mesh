package com.gentics.mesh.search.verticle.eventhandler;

import static com.gentics.mesh.core.rest.MeshEvent.TAG_CREATED;
import static com.gentics.mesh.core.rest.MeshEvent.TAG_DELETED;
import static com.gentics.mesh.core.rest.MeshEvent.TAG_UPDATED;
import static com.gentics.mesh.search.verticle.entity.MeshEntities.findElementByUuidStream;
import static com.gentics.mesh.search.verticle.eventhandler.Util.concat;
import static com.gentics.mesh.search.verticle.eventhandler.Util.requireType;
import static com.gentics.mesh.util.StreamUtil.toStream;

import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.stream.Stream;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.gentics.mesh.core.data.Tag;
import com.gentics.mesh.core.data.TagFamily;
import com.gentics.mesh.core.data.search.request.CreateDocumentRequest;
import com.gentics.mesh.core.data.search.request.SearchRequest;
import com.gentics.mesh.core.rest.MeshEvent;
import com.gentics.mesh.core.rest.event.MeshProjectElementEventModel;
import com.gentics.mesh.etc.config.MeshOptions;
import com.gentics.mesh.etc.config.search.ComplianceMode;
import com.gentics.mesh.search.verticle.MessageEvent;
import com.gentics.mesh.search.verticle.entity.MeshEntities;
import com.gentics.mesh.util.RxUtil;

import io.reactivex.Flowable;

@Singleton
public class TagEventHandler implements EventHandler {
	
	private final MeshHelper helper;
	private final MeshEntities entities;
	private final ComplianceMode complianceMode;

	@Inject
	public TagEventHandler(MeshHelper helper, MeshEntities entities, MeshOptions options) {
		this.helper = helper;
		this.entities = entities;
		this.complianceMode = options.getSearchOptions().getComplianceMode();
	}

	@Override
	public Collection<MeshEvent> handledEvents() {
		return Arrays.asList(TAG_CREATED, TAG_UPDATED, TAG_DELETED);
	}

	@Override
	public Flowable<SearchRequest> handle(MessageEvent messageEvent) {
		return helper.getDb().<Flowable<SearchRequest>>singleTxImmediate(() -> {
			MeshEvent event = messageEvent.event;
			MeshProjectElementEventModel model = requireType(MeshProjectElementEventModel.class, messageEvent.message);
			String projectUuid = model.getProject().getUuid();

			if (event == TAG_CREATED || event == TAG_UPDATED) {
				// We also need to update the tag family
				Optional<Tag> tag = entities.tag.getElement(model);
				Optional<TagFamily> tagFamily = tag.map(Tag::getTagFamily);

				return concat(
					toStream(tag).map(t -> entities.createRequest(t, projectUuid)),
					toStream(tagFamily).map(tf -> entities.createRequest(tf, projectUuid)),
					event == TAG_UPDATED
						? toStream(tag).flatMap(t -> taggedNodes(model, t))
						: Stream.empty()
				).collect(Util.toFlowable());
			} else if (event == TAG_DELETED) {
				// The tag was deleted via a project deletion. The project handler takes care of deleting the tag index.
				if (EventCauseHelper.isProjectDeleteCause(model)) {
					return Flowable.empty();
				} else {
					return Flowable.just(helper.deleteDocumentRequest(Tag.composeIndexName(projectUuid), model.getUuid(), complianceMode));
				}

			} else {
				throw new RuntimeException("Unexpected event " + event.address);
			}
		}).flatMapPublisher(RxUtil.identity());
	}

	private Stream<CreateDocumentRequest> taggedNodes(MeshProjectElementEventModel model, Tag tag) {
		return findElementByUuidStream(helper.getBoot().projectRoot(), model.getProject().getUuid())
			.flatMap(project -> project.getBranchRoot().findAll().stream()
			.flatMap(branch -> tag.getNodes(branch).stream()
			.flatMap(node -> entities.generateNodeRequests(node.getUuid(), project, branch))));
	}
}
