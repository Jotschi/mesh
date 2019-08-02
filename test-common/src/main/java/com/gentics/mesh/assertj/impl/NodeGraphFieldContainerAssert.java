package com.gentics.mesh.assertj.impl;

import static com.gentics.mesh.assertj.MeshAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import org.assertj.core.api.AbstractObjectAssert;

import com.gentics.mesh.core.data.NodeGraphFieldContainer;
import com.gentics.mesh.core.data.schema.SchemaContainerVersion;

public class NodeGraphFieldContainerAssert extends AbstractObjectAssert<NodeGraphFieldContainerAssert, NodeGraphFieldContainer> {

	public NodeGraphFieldContainerAssert(NodeGraphFieldContainer actual) {
		super(actual, NodeGraphFieldContainerAssert.class);
	}

	/**
	 * Assert that the field uses the given schema.
	 * 
	 * @param schemaVersion
	 *            schema container
	 * @return fluent API
	 */
	public NodeGraphFieldContainerAssert isOf(SchemaContainerVersion schemaVersion) {
		// TODO Review
		assertThat(actual.getAnySchemaContainerVersion().getVersion()).as("Schema version").isEqualTo(schemaVersion.getVersion());
		assertThat(actual.getAnySchemaContainerVersion().getUuid()).as("Schema version Uuid").isEqualTo(schemaVersion.getUuid());
		return this;
	}

	/**
	 * Assert that the field container is of the version
	 *
	 * @param version
	 * @return
	 */
	public NodeGraphFieldContainerAssert hasVersion(String version) {
		assertThat(actual.getVersion()).as(descriptionText() + " version").isNotNull().hasToString(version);
		return this;
	}

	/**
	 * Assert that the field container has no previous container
	 * 
	 * @param container
	 * @return fluent API
	 */
	public NodeGraphFieldContainerAssert isFirst() {
		assertThat(actual.getPreviousVersion()).as(descriptionText() + " previous container").isNull();
		return this;
	}

	/**
	 * Assert that the field container has no next container
	 * 
	 * @param container
	 * @return fluent API
	 */
	public NodeGraphFieldContainerAssert isLast() {
		assertThat(actual.getNextVersions()).as(descriptionText() + " next container").isEmpty();
		return this;
	}

	/**
	 * Assert that the field container has the given next container
	 * 
	 * @param container
	 * @return fluent API
	 */
	public NodeGraphFieldContainerAssert hasNext(NodeGraphFieldContainer container) {
		Iterable<NodeGraphFieldContainer> next = (Iterable<NodeGraphFieldContainer>) actual.getNextVersions();
		assertThat(next).as(descriptionText() + " next container").isNotNull().usingFieldByFieldElementComparator().contains(container);
		return this;
	}

	/**
	 * Assert that the field container has the given previous container
	 * 
	 * @param container
	 * @return fluent API
	 */
	public NodeGraphFieldContainerAssert hasPrevious(NodeGraphFieldContainer container) {
		assertThat(actual.getPreviousVersion()).as(descriptionText() + " previous container").isNotNull()
				.isEqualToComparingFieldByField(container);
		return this;
	}
}
