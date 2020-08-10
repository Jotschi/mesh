package com.gentics.mesh.core.data.schema.handler;

import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.gentics.mesh.core.rest.schema.MicroschemaModel;
import com.gentics.mesh.core.rest.schema.change.impl.SchemaChangeModel;

@Singleton
public class MicroschemaComparator extends AbstractFieldSchemaContainerComparator<MicroschemaModel> {

	@Inject
	public MicroschemaComparator() {
	}

	@Override
	public List<SchemaChangeModel> diff(MicroschemaModel containerA, MicroschemaModel containerB) {
		return super.diff(containerA, containerB, MicroschemaModel.class);
	}

}
