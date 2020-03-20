package com.gentics.mesh.core.migration.node;

import com.gentics.mesh.context.NodeMigrationActionContext;

import io.reactivex.Completable;

public interface NodeMigrationHandler {

	/**
	 * Migrate all nodes of a branch referencing the given schema container to the latest version of the schema.
	 *
	 * @param context
	 *            Migration context
	 * @return Completable which is completed once the migration finishes
	 */
	Completable migrateNodes(NodeMigrationActionContext context);

}
