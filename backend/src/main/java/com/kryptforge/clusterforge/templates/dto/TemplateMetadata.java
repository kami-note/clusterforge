package com.kryptforge.clusterforge.templates.dto;

import java.util.List;
import java.util.Map;

public record TemplateMetadata(
	String name,
	String description,
	String version,
	List<String> tags,
	Map<String, String> env,
	List<Integer> ports,
	List<String> volumes
) {}


