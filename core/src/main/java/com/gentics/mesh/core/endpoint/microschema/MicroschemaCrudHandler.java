package com.gentics.mesh.core.endpoint.microschema;

import static com.gentics.mesh.core.data.relationship.GraphPermission.READ_PERM;
import static com.gentics.mesh.core.data.relationship.GraphPermission.UPDATE_PERM;
import static com.gentics.mesh.core.rest.error.Errors.error;
import static com.gentics.mesh.rest.Messages.message;
import static io.netty.handler.codec.http.HttpResponseStatus.FORBIDDEN;
import static io.netty.handler.codec.http.HttpResponseStatus.NO_CONTENT;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;

import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import com.gentics.mesh.cli.BootstrapInitializer;
import com.gentics.mesh.context.InternalActionContext;
import com.gentics.mesh.core.data.Branch;
import com.gentics.mesh.core.data.Project;
import com.gentics.mesh.core.data.User;
import com.gentics.mesh.core.data.dao.MicroschemaDaoWrapper;
import com.gentics.mesh.core.data.dao.UserDaoWrapper;
import com.gentics.mesh.core.data.root.MicroschemaRoot;
import com.gentics.mesh.core.data.schema.Microschema;
import com.gentics.mesh.core.data.schema.MicroschemaVersion;
import com.gentics.mesh.core.data.schema.handler.MicroschemaComparator;
import com.gentics.mesh.core.endpoint.handler.AbstractCrudHandler;
import com.gentics.mesh.core.rest.MeshEvent;
import com.gentics.mesh.core.rest.microschema.impl.MicroschemaModelImpl;
import com.gentics.mesh.core.rest.microschema.impl.MicroschemaResponse;
import com.gentics.mesh.core.rest.schema.MicroschemaModel;
import com.gentics.mesh.core.rest.schema.change.impl.SchemaChangesListModel;
import com.gentics.mesh.core.verticle.handler.CreateAction;
import com.gentics.mesh.core.verticle.handler.HandlerUtilities;
import com.gentics.mesh.core.verticle.handler.LoadAction;
import com.gentics.mesh.core.verticle.handler.LoadAllAction;
import com.gentics.mesh.core.verticle.handler.UpdateAction;
import com.gentics.mesh.core.verticle.handler.WriteLock;
import com.gentics.mesh.graphdb.spi.Database;
import com.gentics.mesh.json.JsonUtil;
import com.gentics.mesh.parameter.SchemaUpdateParameters;
import com.gentics.mesh.util.UUIDUtil;

import dagger.Lazy;

public class MicroschemaCrudHandler extends AbstractCrudHandler<Microschema, MicroschemaResponse> {

	private MicroschemaComparator comparator;

	private Lazy<BootstrapInitializer> boot;

	@Inject
	public MicroschemaCrudHandler(Database db, MicroschemaComparator comparator, Lazy<BootstrapInitializer> boot, HandlerUtilities utils,
		WriteLock writeLock) {
		super(db, utils, writeLock);
		this.comparator = comparator;
		this.boot = boot;
	}

	@Override
	public LoadAction<Microschema> loadAction() {
		return (tx, ac, uuid, perm, errorIfNotFound) -> {
			return tx.data().microschemaDao().loadObjectByUuid(ac, uuid, perm, errorIfNotFound);
		};
	}

	@Override
	public LoadAllAction<Microschema> loadAllAction() {
		return (tx, ac, pagingInfo) -> {
			return tx.data().microschemaDao().findAll(ac, pagingInfo);
		};
	}

	@Override
	public CreateAction<Microschema> createAction() {
		return (tx, ac, batch, uuid) -> {
			return tx.data().microschemaDao().create(ac, batch, uuid);
		};
	}

	@Override
	public UpdateAction<Microschema> updateAction() {
		// Microschemas are updated via migrations
		return null;
	}

	@Override
	public void handleUpdate(InternalActionContext ac, String uuid) {
		validateParameter(uuid, "uuid");

		try (WriteLock lock = writeLock.lock(ac)) {
			/**
			 * The following code delegates the call to the handleUpdate method is very hacky at best. It would be better to move the whole update code into the
			 * MicroschemaContainerImpl#update method and use the regular handlerUtilities. (similar to all other calls) The current code however does not
			 * return a MicroschemaResponse for update requests. Instead a message will be returned. Changing this behaviour would cause a breaking change.
			 * (Changed response model).
			 */
			boolean delegateToCreate = db.tx(tx -> {
				if (!UUIDUtil.isUUID(uuid)) {
					return false;
				}
				MicroschemaDaoWrapper microschemaDao = tx.data().microschemaDao();
				Microschema microschema = microschemaDao.findByUuid(uuid);
				return microschema == null;
			});

			// Delegate to handle update which will create the microschema
			if (delegateToCreate) {
				ac.skipWriteLock();
				super.handleUpdate(ac, uuid);
				return;
			}

			utils.syncTx(ac, tx -> {
				MicroschemaDaoWrapper microschemaDao = tx.data().microschemaDao();
				Microschema schemaContainer = microschemaDao.loadObjectByUuid(ac, uuid, UPDATE_PERM);
				MicroschemaModel requestModel = JsonUtil.readValue(ac.getBodyAsString(), MicroschemaModelImpl.class);
				requestModel.validate();

				SchemaChangesListModel model = new SchemaChangesListModel();
				model.getChanges().addAll(comparator.diff(schemaContainer.getLatestVersion().getSchema(), requestModel));
				String name = schemaContainer.getName();

				if (model.getChanges().isEmpty()) {
					return message(ac, "schema_update_no_difference_detected", name);
				}
				User user = ac.getUser();
				SchemaUpdateParameters updateParams = ac.getSchemaUpdateParameters();
				String version = utils.eventAction(batch -> {
					MicroschemaVersion createdVersion = schemaContainer.getLatestVersion().applyChanges(ac, model, batch);

					if (updateParams.getUpdateAssignedBranches()) {
						Map<Branch, MicroschemaVersion> referencedBranches = schemaContainer.findReferencedBranches();

						// Assign the created version to the found branches
						for (Map.Entry<Branch, MicroschemaVersion> branchEntry : referencedBranches.entrySet()) {
							Branch branch = branchEntry.getKey();

							// Check whether a list of branch names was specified and skip branches which were not included in the list.
							List<String> branchNames = updateParams.getBranchNames();
							if (branchNames != null && !branchNames.isEmpty() && !branchNames.contains(branch.getName())) {
								continue;
							}

							// Assign the new version to the branch
							branch.assignMicroschemaVersion(user, createdVersion, batch);
						}
					}
					return createdVersion.getVersion();
				});

				if (updateParams.getUpdateAssignedBranches()) {
					MeshEvent.triggerJobWorker(boot.get().mesh());
					return message(ac, "schema_updated_migration_invoked", name, version);
				} else {
					return message(ac, "schema_updated_migration_deferred", name, version);
				}

			}, model -> ac.send(model, OK));
		}

	}

	/**
	 * Compare the latest schema version with the given schema model.
	 * 
	 * @param ac
	 * @param uuid
	 *            Schema uuid
	 */
	public void handleDiff(InternalActionContext ac, String uuid) {
		utils.syncTx(ac, tx -> {
			Microschema microschema = tx.data().microschemaDao().loadObjectByUuid(ac, uuid, READ_PERM);
			MicroschemaModel requestModel = JsonUtil.readValue(ac.getBodyAsString(), MicroschemaModelImpl.class);
			requestModel.validate();
			return microschema.getLatestVersion().diff(ac, comparator, requestModel);
		}, model -> ac.send(model, OK));
	}

	/**
	 * Handle a schema apply changes request.
	 * 
	 * @param ac
	 *            Context which contains the changes data
	 * @param schemaUuid
	 *            Schema which should be modified
	 */
	public void handleApplySchemaChanges(InternalActionContext ac, String schemaUuid) {
		try (WriteLock lock = writeLock.lock(ac)) {
			utils.syncTx(ac, tx -> {
				Microschema schema = tx.data().microschemaDao().loadObjectByUuid(ac, schemaUuid, UPDATE_PERM);
				utils.eventAction(batch -> {
					schema.getLatestVersion().applyChanges(ac, batch);
				});
				return message(ac, "migration_invoked", schema.getName());
			}, model -> ac.send(model, OK));
		}
	}

	/**
	 * Handle a microschema read list request.
	 * 
	 * @param ac
	 */
	public void handleReadMicroschemaList(InternalActionContext ac) {
		utils.readElementList(ac, (tx, ac2, pagingInfo) -> {
			return ac.getProject().getMicroschemaContainerRoot().findAll(ac2, pagingInfo);
		});
	}

	/**
	 * Handle a request which will add a microschema to a project.
	 * 
	 * @param ac
	 *            Internal Action Context which also contains the project to which the microschema will be added.
	 * @param microschemaUuid
	 *            Microschema uuid which should be added to the project.
	 */
	public void handleAddMicroschemaToProject(InternalActionContext ac, String microschemaUuid) {
		validateParameter(microschemaUuid, "microschemaUuid");

		utils.syncTx(ac, tx -> {
			Project project = ac.getProject();
			UserDaoWrapper userDao = tx.data().userDao();
			if (!userDao.hasPermission(ac.getUser(), project, UPDATE_PERM)) {
				String projectUuid = project.getUuid();
				throw error(FORBIDDEN, "error_missing_perm", projectUuid, UPDATE_PERM.getRestPerm().getName());
			}
			Microschema microschema = tx.data().microschemaDao().loadObjectByUuid(ac, microschemaUuid, READ_PERM);
			MicroschemaRoot root = project.getMicroschemaContainerRoot();

			// Only assign if the microschema has not already been assigned.
			if (!root.contains(microschema)) {
				// Assign the microschema to the project
				utils.eventAction(batch -> {
					root.addMicroschema(ac.getUser(), microschema, batch);
				});
			}
			return microschema.transformToRestSync(ac, 0);
		}, model -> ac.send(model, OK));
	}

	public void handleRemoveMicroschemaFromProject(InternalActionContext ac, String microschemaUuid) {
		validateParameter(microschemaUuid, "microschemaUuid");

		utils.syncTx(ac, tx -> {
			Project project = ac.getProject();
			String projectUuid = project.getUuid();
			UserDaoWrapper userDao = tx.data().userDao();
			if (!userDao.hasPermission(ac.getUser(), project, UPDATE_PERM)) {
				throw error(FORBIDDEN, "error_missing_perm", projectUuid, UPDATE_PERM.getRestPerm().getName());
			}
			Microschema microschema = tx.data().microschemaDao().loadObjectByUuid(ac, microschemaUuid, READ_PERM);
			MicroschemaRoot root = project.getMicroschemaContainerRoot();
			if (root.contains(microschema)) {
				// Remove the microschema from the project
				utils.eventAction(batch -> {
					root.removeMicroschema(microschema, batch);
				});
			}
		}, () -> ac.send(NO_CONTENT));
	}
}
