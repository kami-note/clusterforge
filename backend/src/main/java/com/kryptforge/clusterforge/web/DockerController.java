package com.kryptforge.clusterforge.web;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.kryptforge.clusterforge.docker.DockerEngineService;
import com.kryptforge.clusterforge.docker.DockerQueryService;
import com.kryptforge.clusterforge.docker.dto.ContainerDetail;
import com.kryptforge.clusterforge.docker.dto.ContainerSummary;

@RestController
@RequestMapping(path = "/api/docker", produces = MediaType.APPLICATION_JSON_VALUE)
public class DockerController {

	private final DockerQueryService dockerQueryService;
	private final DockerEngineService dockerEngineService;

	public DockerController(DockerQueryService dockerQueryService, DockerEngineService dockerEngineService) {
		this.dockerQueryService = dockerQueryService;
		this.dockerEngineService = dockerEngineService;
	}

	@GetMapping("/containers")
	public List<ContainerSummary> listContainers(
		@RequestParam(name = "all", defaultValue = "false") boolean showAll
	) {
		return dockerQueryService.listContainers(showAll);
	}

	@GetMapping("/containers/{id}")
	public ContainerDetail getContainerDetail(@PathVariable("id") String id) {
		return dockerQueryService.getContainerDetail(id);
	}

	@PostMapping("/containers/{id}/start")
	public ResponseEntity<Void> startContainer(@PathVariable("id") String id) {
		dockerEngineService.startContainer(id);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/containers/{id}/stop")
	public ResponseEntity<Void> stopContainer(
		@PathVariable("id") String id,
		@RequestParam(name = "timeout", defaultValue = "10") int timeoutSeconds
	) {
		dockerEngineService.stopContainer(id, timeoutSeconds);
		return ResponseEntity.noContent().build();
	}
}


