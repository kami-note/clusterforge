package com.kryptforge.clusterforge.docker.dto;

import java.util.List;
import java.util.Map;

/**
 * Detalhes completos de um container para consulta por ID.
 */
public record ContainerDetail(
	String id,
	List<String> names,
	String image,
	String imageId,
	String created,
	String state,
	String status,
	Map<String, String> labels,
	List<String> mounts,
	List<String> networks,
	List<String> ports
) {}


