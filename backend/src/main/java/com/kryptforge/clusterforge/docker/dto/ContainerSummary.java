package com.kryptforge.clusterforge.docker.dto;

import java.util.List;

/**
 * DTO imutável para expor informações de containers via API.
 */
public record ContainerSummary(
	String id,
	List<String> names,
	String image,
	String state,
	String status,
	Long created,
	List<String> ports
) {}


