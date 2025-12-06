package com.kryptforge.clusterforge.web;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.kryptforge.clusterforge.clusters.ClusterEnrichmentService;
import com.kryptforge.clusterforge.clusters.ClusterInstance;
import com.kryptforge.clusterforge.clusters.ClusterMonitoringService;
import com.kryptforge.clusterforge.clusters.ClusterService;
import com.kryptforge.clusterforge.clusters.dto.ClusterDtos.ClusterOwnerUpdateRequest;
import com.kryptforge.clusterforge.clusters.dto.ClusterDtos.ClusterResponse;
import com.kryptforge.clusterforge.clusters.dto.ClusterDtos.ClusterStatusUpdateRequest;
import com.kryptforge.clusterforge.clusters.dto.ClusterDtos.ClusterUpdateParamsRequest;
import com.kryptforge.clusterforge.monitoring.ClusterLog;
import com.kryptforge.clusterforge.monitoring.ClusterMetric;

/**
 * Controller REST para gerenciamento de clusters.
 * 
 * <p>
 * Refatorado para seguir o princípio de responsabilidade única:
 * </p>
 * <ul>
 * <li>{@link ClusterEnrichmentService} - Enriquecimento com status Docker</li>
 * <li>{@link ClusterMonitoringService} - Histórico de logs e métricas</li>
 * <li>{@link ClusterService} - Operações CRUD e controle de containers</li>
 * </ul>
 */
@RestController
@RequestMapping(path = "/api/clusters", produces = MediaType.APPLICATION_JSON_VALUE)
public class ClusterController {

	private final ClusterService clusterService;
	private final ClusterEnrichmentService enrichmentService;
	private final ClusterMonitoringService monitoringService;

	public ClusterController(
			ClusterService clusterService,
			ClusterEnrichmentService enrichmentService,
			ClusterMonitoringService monitoringService) {
		this.clusterService = clusterService;
		this.enrichmentService = enrichmentService;
		this.monitoringService = monitoringService;
	}

	// ==================== LISTAGEM ====================

	/**
	 * Lista todos os clusters com status real do Docker.
	 * 
	 * @return lista de clusters enriquecidos (containers deletados são filtrados)
	 */
	@GetMapping
	public List<ClusterResponse> list() {
		return enrichmentService.enrichClusters(clusterService.list());
	}

	/**
	 * Lista clusters de um usuário específico.
	 * 
	 * @param userId ID do usuário
	 * @return lista de clusters do usuário
	 */
	@GetMapping("/user/{userId}")
	public List<ClusterResponse> listByUser(@PathVariable("userId") UUID userId) {
		return enrichmentService.enrichClustersByOwner(clusterService.list(), userId);
	}

	/**
	 * Obtém detalhes de um cluster específico.
	 * 
	 * @param id ID do cluster
	 * @return detalhes do cluster com status atualizado
	 */
	@GetMapping("/{id}")
	public ClusterResponse get(@PathVariable("id") UUID id) {
		ClusterInstance instance = clusterService.get(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "cluster não encontrado"));

		return enrichmentService.enrichSingleCluster(instance);
	}

	// ==================== ATUALIZAÇÃO ====================

	/**
	 * Atualiza o status de um cluster.
	 * 
	 * @param id  ID do cluster
	 * @param req dados do novo status
	 * @return cluster atualizado
	 */
	@PatchMapping(path = "/{id}/status", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ClusterResponse updateStatus(
			@PathVariable("id") UUID id,
			@RequestBody ClusterStatusUpdateRequest req) {
		try {
			ClusterInstance updated = clusterService.updateStatus(id, req.status());
			return enrichmentService.enrichSingleCluster(updated);
		} catch (IllegalArgumentException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
		}
	}

	/**
	 * Atualiza parâmetros de um cluster (limites de recursos, etc).
	 * 
	 * @param id  ID do cluster
	 * @param req novos parâmetros
	 * @return cluster atualizado
	 */
	@PatchMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ClusterResponse updateParams(
			@PathVariable("id") UUID id,
			@RequestBody ClusterUpdateParamsRequest req) {
		try {
			ClusterInstance updated = clusterService.updateParams(id, req.toParams());
			return enrichmentService.enrichSingleCluster(updated);
		} catch (IllegalArgumentException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
		}
	}

	/**
	 * Atualiza o proprietário de um cluster.
	 * 
	 * @param id  ID do cluster
	 * @param req dados do novo proprietário
	 * @return cluster atualizado
	 */
	@PatchMapping(path = "/{id}/owner", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ClusterResponse updateOwner(
			@PathVariable("id") UUID id,
			@RequestBody ClusterOwnerUpdateRequest req) {
		try {
			UUID ownerId = (req.ownerId() == null || req.ownerId().isBlank())
					? null
					: UUID.fromString(req.ownerId());
			ClusterInstance updated = clusterService.updateOwner(id, ownerId);
			return enrichmentService.enrichSingleCluster(updated);
		} catch (IllegalArgumentException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
		}
	}

	// ==================== CONTROLE DE CONTAINERS ====================

	/**
	 * Inicia um cluster.
	 * 
	 * @param id ID do cluster
	 * @return cluster com status atualizado
	 */
	@PostMapping("/{id}/start")
	public ClusterResponse startCluster(@PathVariable("id") UUID id) {
		ClusterInstance updated = clusterService.startContainer(id);
		return enrichmentService.enrichSingleCluster(updated);
	}

	/**
	 * Para um cluster.
	 * 
	 * @param id             ID do cluster
	 * @param timeoutSeconds tempo máximo para aguardar parada graceful
	 * @return cluster com status atualizado
	 */
	@PostMapping("/{id}/stop")
	public ClusterResponse stopCluster(
			@PathVariable("id") UUID id,
			@RequestParam(name = "timeout", defaultValue = "10") int timeoutSeconds) {
		ClusterInstance updated = clusterService.stopContainer(id, timeoutSeconds);
		return enrichmentService.enrichSingleCluster(updated);
	}

	/**
	 * Reinicia um cluster.
	 * 
	 * @param id             ID do cluster
	 * @param timeoutSeconds tempo máximo para aguardar restart
	 * @return cluster com status atualizado
	 */
	@PostMapping("/{id}/restart")
	public ClusterResponse restartCluster(
			@PathVariable("id") UUID id,
			@RequestParam(name = "timeout", defaultValue = "10") int timeoutSeconds) {
		ClusterInstance updated = clusterService.restartContainer(id, timeoutSeconds);
		return enrichmentService.enrichSingleCluster(updated);
	}

	/**
	 * Deleta um cluster.
	 * 
	 * @param id ID do cluster
	 * @return 204 No Content
	 */
	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable("id") UUID id) {
		clusterService.delete(id);
		return ResponseEntity.noContent().build();
	}

	// ==================== MONITORAMENTO ====================

	/**
	 * Obtém histórico de logs de um cluster.
	 * 
	 * @param id        ID do cluster
	 * @param page      número da página (padrão: 0)
	 * @param size      tamanho da página (padrão: 100)
	 * @param startTime data/hora inicial (opcional, formato ISO)
	 * @param endTime   data/hora final (opcional, formato ISO)
	 * @return página de logs
	 */
	@GetMapping("/{id}/logs/history")
	public Page<ClusterLog> getLogsHistory(
			@PathVariable("id") UUID id,
			@RequestParam(name = "page", defaultValue = "0") int page,
			@RequestParam(name = "size", defaultValue = "100") int size,
			@RequestParam(name = "startTime", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startTime,
			@RequestParam(name = "endTime", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endTime) {

		return monitoringService.getLogsHistory(id, page, size, startTime, endTime);
	}

	/**
	 * Obtém histórico de métricas de um cluster.
	 * 
	 * @param id        ID do cluster
	 * @param page      número da página (padrão: 0)
	 * @param size      tamanho da página (padrão: 100)
	 * @param startTime data/hora inicial (opcional, formato ISO)
	 * @param endTime   data/hora final (opcional, formato ISO)
	 * @return página de métricas
	 */
	@GetMapping("/{id}/metrics/history")
	public Page<ClusterMetric> getMetricsHistory(
			@PathVariable("id") UUID id,
			@RequestParam(name = "page", defaultValue = "0") int page,
			@RequestParam(name = "size", defaultValue = "100") int size,
			@RequestParam(name = "startTime", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startTime,
			@RequestParam(name = "endTime", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endTime) {

		return monitoringService.getMetricsHistory(id, page, size, startTime, endTime);
	}

	/**
	 * Obtém estatísticas de logs e métricas de um cluster.
	 * 
	 * @param id ID do cluster
	 * @return estatísticas (contagem de logs e métricas)
	 */
	@GetMapping("/{id}/stats")
	public ResponseEntity<Map<String, Object>> getClusterStats(@PathVariable("id") UUID id) {
		return ResponseEntity.ok(monitoringService.getStats(id));
	}
}
