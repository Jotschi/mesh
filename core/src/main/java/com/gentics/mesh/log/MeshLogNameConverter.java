package com.gentics.mesh.log;

import com.gentics.mesh.cli.MeshNameProvider;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

public class MeshLogNameConverter extends ClassicConverter {

	@Override
	public String convert(ILoggingEvent event) {
		return MeshNameProvider.getInstance().getName();
	}
}
