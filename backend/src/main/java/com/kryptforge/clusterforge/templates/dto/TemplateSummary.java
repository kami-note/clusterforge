package com.kryptforge.clusterforge.templates.dto;

import java.time.Instant;

public record TemplateSummary(
	String name,
	String relativePath,
	boolean hasMetadata,
	Instant updatedAt
) {}


