package com.kryptforge.clusterforge.templates.dto;

import java.util.List;
import java.util.Map;

public record TemplateInstantiateRequest(
	String name,
	Map<String, String> env,
	List<String> ports,
	List<String> binds
) {}


