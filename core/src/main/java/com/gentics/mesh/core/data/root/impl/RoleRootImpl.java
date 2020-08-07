package com.gentics.mesh.core.data.root.impl;

import static com.gentics.mesh.core.data.relationship.GraphPermission.CREATE_PERM;
import static com.gentics.mesh.core.data.relationship.GraphPermission.READ_PERM;
import static com.gentics.mesh.core.data.relationship.GraphRelationships.HAS_ROLE;
import static com.gentics.mesh.core.rest.error.Errors.conflict;
import static com.gentics.mesh.core.rest.error.Errors.error;
import static com.gentics.mesh.madl.index.EdgeIndexDefinition.edgeIndex;
import static com.gentics.mesh.util.CompareUtils.shouldUpdate;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.FORBIDDEN;
import static io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import com.gentics.madl.index.IndexHandler;
import com.gentics.madl.type.TypeHandler;
import com.gentics.mesh.cli.BootstrapInitializer;
import com.gentics.mesh.context.BulkActionContext;
import com.gentics.mesh.context.InternalActionContext;
import com.gentics.mesh.core.data.Group;
import com.gentics.mesh.core.data.MeshAuthUser;
import com.gentics.mesh.core.data.MeshVertex;
import com.gentics.mesh.core.data.Role;
import com.gentics.mesh.core.data.User;
import com.gentics.mesh.core.data.dao.UserDaoWrapper;
import com.gentics.mesh.core.data.generic.MeshVertexImpl;
import com.gentics.mesh.core.data.impl.GroupImpl;
import com.gentics.mesh.core.data.impl.RoleImpl;
import com.gentics.mesh.core.data.page.Page;
import com.gentics.mesh.core.data.page.impl.DynamicTransformablePageImpl;
import com.gentics.mesh.core.data.relationship.GraphPermission;
import com.gentics.mesh.core.data.root.RoleRoot;
import com.gentics.mesh.core.rest.role.RoleCreateRequest;
import com.gentics.mesh.core.rest.role.RoleResponse;
import com.gentics.mesh.core.rest.role.RoleUpdateRequest;
import com.gentics.mesh.event.EventQueueBatch;
import com.gentics.mesh.parameter.GenericParameters;
import com.gentics.mesh.parameter.PagingParameters;
import com.gentics.mesh.parameter.value.FieldsSet;
import com.syncleus.ferma.traversals.VertexTraversal;

import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

/**
 * @see RoleRoot
 */
public class RoleRootImpl extends AbstractRootVertex<Role> implements RoleRoot {

	private static final Logger log = LoggerFactory.getLogger(RoleRootImpl.class);

	public static void init(TypeHandler type, IndexHandler index) {
		type.createVertexType(RoleRootImpl.class, MeshVertexImpl.class);
		index.createIndex(edgeIndex(HAS_ROLE).withInOut().withOut());
	}

	@Override
	public Class<? extends Role> getPersistanceClass() {
		return RoleImpl.class;
	}

	@Override
	public String getRootLabel() {
		return HAS_ROLE;
	}

	@Override
	public void addRole(Role role) {
		if (log.isDebugEnabled()) {
			log.debug("Adding role {" + role.getUuid() + ":" + role.getName() + "#" + role.id() + "} to roleRoot {" + id() + "}");
		}
		addItem(role);
	}

	@Override
	public void removeRole(Role role) {
		// TODO delete the role? unlink from all groups? how is ferma / blueprint handling this. Neo4j would explode when trying to remove a node that still has
		// connecting edges.
		removeItem(role);
	}

	@Override
	public Role create(String name, User creator, String uuid) {
		Role role = getGraph().addFramedVertex(RoleImpl.class);
		if (uuid != null) {
			role.setUuid(uuid);
		}
		role.setName(name);
		role.setCreated(creator);
		addRole(role);
		return role;
	}

	public Role create(InternalActionContext ac, EventQueueBatch batch, String uuid) {
		RoleCreateRequest requestModel = ac.fromJson(RoleCreateRequest.class);
		String roleName = requestModel.getName();
		UserDaoWrapper userRoot = mesh().boot().userDao();

		MeshAuthUser requestUser = ac.getUser();
		if (StringUtils.isEmpty(roleName)) {
			throw error(BAD_REQUEST, "error_name_must_be_set");
		}

		Role conflictingRole = findByName(roleName);
		if (conflictingRole != null) {
			throw conflict(conflictingRole.getUuid(), roleName, "role_conflicting_name");
		}

		// TODO use non-blocking code here
		if (!userRoot.hasPermission(requestUser, this, CREATE_PERM)) {
			throw error(FORBIDDEN, "error_missing_perm", this.getUuid(), CREATE_PERM.getRestPerm().getName());
		}

		Role role = create(requestModel.getName(), requestUser, uuid);
		userRoot.inheritRolePermissions(requestUser, this, role);
		batch.add(role.onCreated());
		return role;

	}

	@Override
	public void delete(BulkActionContext bac) {
		throw error(INTERNAL_SERVER_ERROR, "The global role root can't be deleted.");
	}

	@Override
	public RoleResponse transformToRestSync(Role role, InternalActionContext ac, int level, String... languageTags) {
		GenericParameters generic = ac.getGenericParameters();
		FieldsSet fields = generic.getFields();

		RoleResponse restRole = new RoleResponse();

		if (fields.has("name")) {
			restRole.setName(role.getName());
		}

		if (fields.has("groups")) {
			setGroups(role, ac, restRole);
		}
		role.fillCommonRestFields(ac, fields, restRole);

		setRolePermissions(role, ac, restRole);
		return restRole;

	}

	private void setGroups(Role role, InternalActionContext ac, RoleResponse restRole) {
		for (Group group : role.getGroups()) {
			restRole.getGroups().add(group.transformToReference());
		}
	}

	@Override
	public Page<? extends Group> getGroups(Role role, User user, PagingParameters pagingInfo) {
		VertexTraversal<?, ?, ?> traversal = role.out(HAS_ROLE);
		return new DynamicTransformablePageImpl<Group>(user, traversal, pagingInfo, READ_PERM, GroupImpl.class);
	}

	@Override
	public Set<GraphPermission> getPermissions(Role role, MeshVertex vertex) {
		Set<GraphPermission> permissions = new HashSet<>();
		GraphPermission[] possiblePermissions = vertex.hasPublishPermissions()
			? GraphPermission.values()
			: GraphPermission.basicPermissions();

		for (GraphPermission permission : possiblePermissions) {
			if (hasPermission(role, permission, vertex)) {
				permissions.add(permission);
			}
		}
		return permissions;
	}

	@Override
	public boolean hasPermission(Role role, GraphPermission permission, MeshVertex vertex) {
		Set<String> allowedUuids = vertex.property(permission.propertyKey());
		return allowedUuids != null && allowedUuids.contains(role.getUuid());
	}

	@Override
	public void grantPermissions(Role role, MeshVertex vertex, GraphPermission... permissions) {
		for (GraphPermission permission : permissions) {
			Set<String> allowedRoles = vertex.property(permission.propertyKey());
			if (allowedRoles == null) {
				vertex.property(permission.propertyKey(), Collections.singleton(role.getUuid()));
			} else {
				allowedRoles.add(role.getUuid());
				vertex.property(permission.propertyKey(), allowedRoles);
			}
		}
	}

	@Override
	public void revokePermissions(Role role, MeshVertex vertex, GraphPermission... permissions) {
		boolean permissionRevoked = false;
		for (GraphPermission permission : permissions) {
			Set<String> allowedRoles = vertex.property(permission.propertyKey());
			if (allowedRoles != null) {
				permissionRevoked = allowedRoles.remove(role.getUuid()) || permissionRevoked;
				vertex.property(permission.propertyKey(), allowedRoles);
			}
		}

		if (permissionRevoked) {
			mesh().permissionCache().clear();
		}
	}

	@Override
	public void delete(Role role, BulkActionContext bac) {
		bac.add(role.onDeleted());
		role.getVertex().remove();
		bac.process();
		mesh().permissionCache().clear();
	}

	@Override
	public boolean update(Role role, InternalActionContext ac, EventQueueBatch batch) {
		RoleUpdateRequest requestModel = ac.fromJson(RoleUpdateRequest.class);
		BootstrapInitializer boot = mesh().boot();
		if (shouldUpdate(requestModel.getName(), role.getName())) {
			// Check for conflict
			Role roleWithSameName = boot.roleRoot().findByName(requestModel.getName());
			if (roleWithSameName != null && !roleWithSameName.getUuid().equals(getUuid())) {
				throw conflict(roleWithSameName.getUuid(), requestModel.getName(), "role_conflicting_name");
			}

			role.setName(requestModel.getName());
			batch.add(role.onUpdated());
			return true;
		}
		return false;
	}

}
