package com.kryptforge.clusterforge.web;

import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.kryptforge.clusterforge.clusters.ClusterService;
import com.kryptforge.clusterforge.docker.DockerEngineService;
import com.kryptforge.clusterforge.docker.DockerQueryService;
import com.kryptforge.clusterforge.docker.DockerStreamService;
import com.kryptforge.clusterforge.docker.dto.ContainerDetail;
import com.kryptforge.clusterforge.docker.dto.ContainerLogsResponse;
import com.kryptforge.clusterforge.docker.dto.ContainerSummary;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping(path = "/api/docker", produces = MediaType.APPLICATION_JSON_VALUE)
public class DockerController {

	private final DockerQueryService dockerQueryService;
	private final DockerEngineService dockerEngineService;
	private final DockerStreamService dockerStreamService;
	private final ClusterService clusterService;

	public DockerController(
		DockerQueryService dockerQueryService,
		DockerEngineService dockerEngineService,
		DockerStreamService dockerStreamService,
		ClusterService clusterService
	) {
		this.dockerQueryService = dockerQueryService;
		this.dockerEngineService = dockerEngineService;
		this.dockerStreamService = dockerStreamService;
		this.clusterService = clusterService;
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

	@GetMapping("/containers/{id}/logs")
	public ResponseEntity<?> getContainerLogs(
		@PathVariable("id") String id,
		@RequestParam(name = "tail", required = false) Integer tailLines,
		@RequestParam(name = "since", required = false) Integer sinceSeconds
	) {
		try {
			ContainerLogsResponse response = dockerEngineService.getContainerLogs(id, true, true, tailLines, sinceSeconds);
			return ResponseEntity.ok(response);
		} catch (Exception e) {
			return ResponseEntity.status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
				.body(Map.of(
					"error", e.getMessage() != null ? e.getMessage() : "Erro ao obter logs",
					"containerId", id
				));
		}
	}

	@GetMapping(path = "/containers/{id}/metrics/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public SseEmitter streamContainerMetrics(
		@PathVariable("id") String id,
		@RequestParam(name = "timeoutMillis", defaultValue = "300000") long timeoutMillis
	) {
		return dockerStreamService.streamContainerStats(id, timeoutMillis);
	}

	@GetMapping(path = "/containers/{id}/logs/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public SseEmitter streamContainerLogs(
		@PathVariable("id") String id,
		@RequestParam(name = "timeoutMillis", defaultValue = "300000") long timeoutMillis,
		@RequestParam(name = "tail", required = false) Integer tailLines,
		@RequestParam(name = "since", required = false) Integer sinceSeconds
	) {
		return dockerStreamService.streamContainerLogs(id, timeoutMillis, tailLines, sinceSeconds);
	}

	/**
	 * Stream de métricas de todos os containers visíveis ao usuário atual.
	 * Admin vê todos os clusters, usuário comum vê apenas os seus.
	 * Métricas são enviadas periodicamente (não em tempo real) para reduzir carga.
	 * 
	 * @param timeoutMillis Timeout do SSE (padrão: 5 minutos)
	 * @param intervalMillis Intervalo entre atualizações em ms (padrão: 5 segundos)
	 * @return SseEmitter com métricas de todos os clusters visíveis
	 */
	@GetMapping(path = "/clusters/metrics/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public SseEmitter streamAllClustersMetrics(
		@RequestParam(name = "timeoutMillis", defaultValue = "300000") long timeoutMillis,
		@RequestParam(name = "intervalMillis", defaultValue = "5000") long intervalMillis
	) {
		// Obter clusters visíveis ao usuário atual (já filtra por permissões)
		var clusters = clusterService.list();
		return dockerStreamService.streamAllClustersMetrics(clusters, timeoutMillis, intervalMillis);
	}

}


