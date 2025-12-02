package com.kryptforge.clusterforge.docker;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.kryptforge.clusterforge.docker.dto.ContainerSummary;

/**
 * Camada de consulta/transformação reutilizável para expor dados via API.
 * Reutiliza o DockerEngineService e mapeia para DTOs estáveis.
 */
@Service
public class DockerQueryService {

	private final DockerEngineService engineService;

	public DockerQueryService(DockerEngineService engineService) {
		this.engineService = Objects.requireNonNull(engineService, "engineService");
	}

	public List<ContainerSummary> listContainers(boolean showAll) {
		return engineService.listContainers(showAll)
			.stream()
			.map(ContainerMapper::toSummary)
			.collect(Collectors.toList());
	}

	public com.kryptforge.clusterforge.docker.dto.ContainerDetail getContainerDetail(String containerId) {
		var resp = engineService.inspectContainer(containerId);
		return ContainerMapper.toDetail(resp);
	}
}


