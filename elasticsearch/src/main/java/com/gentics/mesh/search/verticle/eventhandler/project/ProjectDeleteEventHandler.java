package com.gentics.mesh.search.verticle.eventhandler.project;

import static com.gentics.mesh.core.rest.MeshEvent.PROJECT_DELETED;
import static com.gentics.mesh.core.rest.common.ContainerType.DRAFT;
import static com.gentics.mesh.core.rest.common.ContainerType.PUBLISHED;
import static com.gentics.mesh.search.verticle.eventhandler.Util.requireType;

import java.util.Arrays;
import java.util.Collection;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.gentics.mesh.core.data.NodeGraphFieldContainer;
import com.gentics.mesh.core.data.Tag;
import com.gentics.mesh.core.data.TagFamily;
import com.gentics.mesh.core.data.search.request.DropIndexRequest;
import com.gentics.mesh.core.data.search.request.SearchRequest;
import com.gentics.mesh.core.rest.MeshEvent;
import com.gentics.mesh.core.rest.event.impl.MeshElementEventModelImpl;
import com.gentics.mesh.search.verticle.MessageEvent;
import com.gentics.mesh.search.verticle.eventhandler.EventHandler;

import io.reactivex.Flowable;

@Singleton
public class ProjectDeleteEventHandler implements EventHandler {

	@Inject
	public ProjectDeleteEventHandler() {
	}

	@Override
	public Flowable<SearchRequest> handle(MessageEvent messageEvent) {
		MeshElementEventModelImpl model = requireType(MeshElementEventModelImpl.class, messageEvent.message);
		String projectUuid = model.getUuid();
		String draftIndex = NodeGraphFieldContainer.composeIndexName(projectUuid, "*", "*", DRAFT);
		String publishedIndex = NodeGraphFieldContainer.composeIndexName(projectUuid, "*", "*", PUBLISHED);

		DropIndexRequest dropTagIndex = new DropIndexRequest(Tag.composeIndexName(projectUuid));
		DropIndexRequest dropTagFamilyIndex = new DropIndexRequest(TagFamily.composeIndexName(projectUuid));

		return Flowable.fromArray(
			new DropIndexRequest(draftIndex),
			new DropIndexRequest(publishedIndex),
			// This is handled by the simple event handler
			// helper.deleteDocumentRequest(Project.composeIndexName(), model.getUuid()),
			dropTagIndex,
			dropTagFamilyIndex);
	}

	@Override
	public Collection<MeshEvent> handledEvents() {
		return Arrays.asList(PROJECT_DELETED);
	}

}
