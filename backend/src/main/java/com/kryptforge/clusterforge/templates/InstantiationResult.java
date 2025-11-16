package com.kryptforge.clusterforge.templates;

import java.util.List;

/**
 * Resultado da instanciação de um template, incluindo containerId e portas mapeadas.
 */
public record InstantiationResult(
	String containerId,
	List<String> mappedPorts
) {}

