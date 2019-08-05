package com.gentics.mesh.core.rest.microschema.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.gentics.mesh.core.rest.microschema.MicroschemaModel;
import com.gentics.mesh.core.rest.schema.FieldSchema;

import io.vertx.core.json.JsonObject;

public class MicroschemaUpdateRequest implements MicroschemaModel {

	@JsonProperty(required = false)
	@JsonPropertyDescription("Version of the microschema")
	private String version;

	@JsonProperty(required = false)
	@JsonPropertyDescription("Description of the microschema")
	private String description;

	@JsonProperty(required = false)
	@JsonPropertyDescription("Name of the microschema")
	private String name;

	@JsonProperty(required = false)
	@JsonPropertyDescription("Meta data of the schema.")
	private JsonObject meta;

	@JsonProperty(required = false)
	@JsonPropertyDescription("Additional elasticsearch index configuration. This can be used to setup custom analyzers and filters.")
	private JsonObject elasticsearch;

	@JsonPropertyDescription("List of microschema fields")
	private List<FieldSchema> fields = new ArrayList<>();

	@Override
	public String getVersion() {
		return version;
	}

	@Override
	public MicroschemaUpdateRequest setVersion(String version) {
		this.version = version;
		return this;
	}

	@Override
	public String getDescription() {
		return description;
	}

	@Override
	public MicroschemaUpdateRequest setDescription(String description) {
		this.description = description;
		return this;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public MicroschemaUpdateRequest setName(String name) {
		this.name = name;
		return this;
	}

	@Override
	public JsonObject getMeta() {
		return meta;
	}

	@Override
	public MicroschemaUpdateRequest setMeta(JsonObject meta) {
		this.meta = meta;
		return this;
	}

	@Override
	public JsonObject getElasticsearch() {
		return elasticsearch;
	}

	@Override
	public MicroschemaUpdateRequest setElasticsearch(JsonObject elasticsearch) {
		this.elasticsearch = elasticsearch;
		return this;
	}

	@Override
	public List<FieldSchema> getFields() {
		return fields;
	}

	@Override
	public MicroschemaUpdateRequest setFields(List<FieldSchema> fields) {
		this.fields = fields;
		return this;
	}

	@Override
	public String toString() {
		String fields = getFields().stream().map(field -> field.getName()).collect(Collectors.joining(","));
		return getName() + " fields: {" + fields + "}";
	}

}
