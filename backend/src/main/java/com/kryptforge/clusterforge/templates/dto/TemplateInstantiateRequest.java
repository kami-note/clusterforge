package com.kryptforge.clusterforge.templates.dto;

import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;

public record TemplateInstantiateRequest(
	@NotBlank(message = "name é obrigatório")
	String name,
	Map<String, String> env,
	List<String> ports,
	List<String> binds,
	@Min(value = 1, message = "cpuLimitPercent deve ser no mínimo 1%")
	@Max(value = 100, message = "cpuLimitPercent deve ser no máximo 100%")
	Integer cpuLimitPercent,
	@Min(value = 1, message = "memoryLimitMb deve ser no mínimo 1 MB")
	@Max(value = 32768, message = "memoryLimitMb deve ser no máximo 32768 MB (32 GB)")
	Long memoryLimitMb
) {}


