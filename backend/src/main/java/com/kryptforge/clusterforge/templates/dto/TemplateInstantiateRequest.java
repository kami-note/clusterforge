package com.kryptforge.clusterforge.templates.dto;

import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.NotBlank;

public record TemplateInstantiateRequest(
	@NotBlank(message = "name é obrigatório")
	String name,
	Map<String, String> env,
	List<String> ports,
	List<String> binds
) {}


