package com.kryptforge.clusterforge.templates.dto;

import java.util.List;

public record TemplateDetail(
	String name,
	String relativePath,
	boolean composePresent,
	List<TemplateFileEntry> files,
	TemplateMetadata metadata
) {}


