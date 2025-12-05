package com.kryptforge.clusterforge.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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

import com.kryptforge.clusterforge.clusters.ClusterInstance;
import com.kryptforge.clusterforge.clusters.ClusterService;
import com.kryptforge.clusterforge.clusters.ClusterStatus;
import com.kryptforge.clusterforge.clusters.dto.ClusterDtos.ClusterOwnerUpdateRequest;
import com.kryptforge.clusterforge.clusters.dto.ClusterDtos.ClusterResponse;
import com.kryptforge.clusterforge.clusters.dto.ClusterDtos.ClusterStatusUpdateRequest;
import com.kryptforge.clusterforge.clusters.dto.ClusterDtos.ClusterUpdateParamsRequest;
import com.kryptforge.clusterforge.docker.DockerEngineService;
import com.kryptforge.clusterforge.docker.util.DockerStatusMapper;
import com.kryptforge.clusterforge.monitoring.ClusterLog;
import com.kryptforge.clusterforge.monitoring.ClusterLogRepository;
import com.kryptforge.clusterforge.monitoring.ClusterMetric;
import com.kryptforge.clusterforge.monitoring.ClusterMetricRepository;
import com.kryptforge.clusterforge.users.User;
import com.kryptforge.clusterforge.users.UserRepository;

@RestController
@RequestMapping(path = "/api/clusters", produces = MediaType.APPLICATION_JSON_VALUE)
public class ClusterController {

	private static final Logger log = LoggerFactory.getLogger(ClusterController.class);

	private final ClusterService service;
	private final DockerEngineService dockerEngineService;
	private final UserRepository userRepository;
	private final ClusterLogRepository logRepository;
	private final ClusterMetricRepository metricRepository;

	public ClusterController(
			ClusterService service,
			DockerEngineService dockerEngineService,
			UserRepository userRepository,
			ClusterLogRepository logRepository,
			ClusterMetricRepository metricRepository) {
		this.service = service;
		this.dockerEngineService = dockerEngineService;
		this.userRepository = userRepository;
		this.logRepository = logRepository;
		this.metricRepository = metricRepository;
	}

	// POST /api/clusters removido - use POST /api/templates/{name}/instantiate para
	// criar clusters
	// A instanciação de template já cria o container Docker e persiste no banco

	@GetMapping
	public List<ClusterResponse> list() {
		// OTIMIZAÇÃO #3: Paralelizar enriquecimento com status do Docker
		// Reduz latência da listagem em até 80-90% com múltiplos clusters
		return service.list().parallelStream()
				.map(this::enrichWithDockerStatus)
				.filter(response -> response != null)
				.toList();
	}

	/**
	 * Obtém o status real do container Docker e mapeia para ClusterStatus.
	 * Usa DockerStatusMapper para centralizar a lógica de mapeamento.
	 * 
	 * @param containerId ID do container Docker
	 * @return status mapeado do Docker ou DELETED se container não existir
	 */
	private ClusterStatus getDockerStatus(String containerId) {
		try {
			var inspect = dockerEngineService.inspectContainer(containerId);
			if (inspect != null && inspect.getState() != null) {
				return DockerStatusMapper.fromContainerState(inspect.getState());
			}
		} catch (com.github.dockerjava.api.exception.NotFoundException e) {
			return ClusterStatus.DELETED;
		} catch (Exception e) {
			log.debug("Erro ao obter status do Docker para container {}: {}", containerId, e.getMessage());
			return ClusterStatus.ERROR;
		}
		return ClusterStatus.ERROR;
	}

	/**
	 * Enriquece uma instância com status real do Docker.
	 * 
	 * @param instance Instância do cluster
	 * @return ClusterResponse com status atualizado ou null se container foi
	 *         deletado
	 */
	private ClusterResponse enrichWithDockerStatus(ClusterInstance instance) {
		if (instance.getContainerId() == null || instance.getContainerId().isBlank()) {
			return ClusterResponse.from(instance, instance.getStatus(), resolveOwnerName(instance.getOwnerId()));
		}

		try {
			ClusterStatus dockerStatus = getDockerStatus(instance.getContainerId());

			if (dockerStatus == ClusterStatus.DELETED) {
				log.debug("Container {} deletado, filtrando da listagem", instance.getContainerId());
				return null;
			}

			return ClusterResponse.from(instance, dockerStatus, resolveOwnerName(instance.getOwnerId()));
		} catch (Exception e) {
			log.warn("Erro ao obter status do Docker para container {}: {}",
					instance.getContainerId(), e.getMessage());
			return ClusterResponse.from(instance, instance.getStatus(), resolveOwnerName(instance.getOwnerId()));
		}
	}

	@GetMapping("/user/{userId}")
	public List<ClusterResponse> listByUser(@PathVariable("userId") UUID userId) {
		// OTIMIZAÇÃO #3: Paralelizar enriquecimento com status do Docker
		return service.list().parallelStream()
				.filter(instance -> userId.equals(instance.getOwnerId()))
				.map(this::enrichWithDockerStatus)
				.filter(response -> response != null)
				.toList();
	}

	@GetMapping("/{id}")
	public ClusterResponse get(@PathVariable("id") UUID id) {
		ClusterInstance instance = service.get(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "cluster não encontrado"));

		// Se tem containerId, busca status do Docker; senão usa status do banco
		ClusterStatus status = (instance.getContainerId() != null && !instance.getContainerId().isBlank())
				? getDockerStatus(instance.getContainerId())
				: instance.getStatus();

		return ClusterResponse.from(instance, status, resolveOwnerName(instance.getOwnerId()));
	}

	@PatchMapping(path = "/{id}/status", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ClusterResponse updateStatus(@PathVariable("id") UUID id, @RequestBody ClusterStatusUpdateRequest req) {
		try {
			ClusterInstance updated = service.updateStatus(id, req.status());
			return ClusterResponse.from(updated, updated.getStatus(), resolveOwnerName(updated.getOwnerId()));
		} catch (IllegalArgumentException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
		}
	}

	@PatchMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ClusterResponse updateParams(@PathVariable("id") UUID id, @RequestBody ClusterUpdateParamsRequest req) {
		try {
			ClusterInstance updated = service.updateParams(id, req.toParams());
			return ClusterResponse.from(updated, updated.getStatus(), resolveOwnerName(updated.getOwnerId()));
		} catch (IllegalArgumentException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
		}
	}

	@PatchMapping(path = "/{id}/owner", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ClusterResponse updateOwner(@PathVariable("id") UUID id, @RequestBody ClusterOwnerUpdateRequest req) {
		try {
			UUID ownerId = (req.ownerId() == null || req.ownerId().isBlank()) ? null : UUID.fromString(req.ownerId());
			ClusterInstance updated = service.updateOwner(id, ownerId);
			ClusterStatus status = (updated.getContainerId() != null && !updated.getContainerId().isBlank())
					? getDockerStatus(updated.getContainerId())
					: updated.getStatus();
			return ClusterResponse.from(updated, status, resolveOwnerName(updated.getOwnerId()));
		} catch (IllegalArgumentException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
		}
	}

	@PostMapping("/{id}/start")
	public ClusterResponse startCluster(@PathVariable("id") UUID id) {
		ClusterInstance updated = service.startContainer(id);
		// Usa status do banco que foi atualizado pelo service (garante consistência
		// imediata)
		return ClusterResponse.from(updated, updated.getStatus(), resolveOwnerName(updated.getOwnerId()));
	}

	@PostMapping("/{id}/stop")
	public ClusterResponse stopCluster(
			@PathVariable("id") UUID id,
			@RequestParam(name = "timeout", defaultValue = "10") int timeoutSeconds) {
		ClusterInstance updated = service.stopContainer(id, timeoutSeconds);
		// Usa status do banco que foi atualizado pelo service (garante consistência
		// imediata)
		return ClusterResponse.from(updated, updated.getStatus(), resolveOwnerName(updated.getOwnerId()));
	}

	@PostMapping("/{id}/restart")
	public ClusterResponse restartCluster(
			@PathVariable("id") UUID id,
			@RequestParam(name = "timeout", defaultValue = "10") int timeoutSeconds) {
		ClusterInstance updated = service.restartContainer(id, timeoutSeconds);
		// Usa status do banco que foi atualizado pelo service (garante consistência
		// imediata)
		return ClusterResponse.from(updated, updated.getStatus(), resolveOwnerName(updated.getOwnerId()));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable("id") UUID id) {
		service.delete(id);
		return ResponseEntity.noContent().build();
	}

	/**
	 * Obtém histórico de logs de um cluster.
	 * 
	 * @param id        ID do cluster
	 * @param page      Número da página (padrão: 0)
	 * @param size      Tamanho da página (padrão: 100)
	 * @param startTime Data/hora inicial (opcional, formato ISO)
	 * @param endTime   Data/hora final (opcional, formato ISO)
	 * @return Página de logs
	 */
	@GetMapping("/{id}/logs/history")
	public Page<ClusterLog> getLogsHistory(
			@PathVariable("id") UUID id,
			@RequestParam(name = "page", defaultValue = "0") int page,
			@RequestParam(name = "size", defaultValue = "100") int size,
			@RequestParam(name = "startTime", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startTime,
			@RequestParam(name = "endTime", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endTime) {
		// Verificar se cluster existe
		service.get(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "cluster não encontrado"));

		Pageable pageable = PageRequest.of(page, size);

		if (startTime != null && endTime != null) {
			return logRepository.findByClusterIdAndTimestampBetween(id, startTime, endTime, pageable);
		} else {
			return logRepository.findByClusterIdOrderByTimestampDesc(id, pageable);
		}
	}

	/**
	 * Obtém histórico de métricas de um cluster.
	 * 
	 * @param id        ID do cluster
	 * @param page      Número da página (padrão: 0)
	 * @param size      Tamanho da página (padrão: 100)
	 * @param startTime Data/hora inicial (opcional, formato ISO)
	 * @param endTime   Data/hora final (opcional, formato ISO)
	 * @return Página de métricas
	 */
	@GetMapping("/{id}/metrics/history")
	public Page<ClusterMetric> getMetricsHistory(
			@PathVariable("id") UUID id,
			@RequestParam(name = "page", defaultValue = "0") int page,
			@RequestParam(name = "size", defaultValue = "100") int size,
			@RequestParam(name = "startTime", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startTime,
			@RequestParam(name = "endTime", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endTime) {
		// Verificar se cluster existe
		service.get(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "cluster não encontrado"));

		Pageable pageable = PageRequest.of(page, size);

		if (startTime != null && endTime != null) {
			return metricRepository.findByClusterIdAndTimestampBetween(id, startTime, endTime, pageable);
		} else {
			return metricRepository.findByClusterIdOrderByTimestampDesc(id, pageable);
		}
	}

	/**
	 * Obtém estatísticas de logs e métricas de um cluster.
	 * 
	 * @param id ID do cluster
	 * @return Estatísticas (contagem de logs e métricas)
	 */
	@GetMapping("/{id}/stats")
	public ResponseEntity<?> getClusterStats(@PathVariable("id") UUID id) {
		// Verificar se cluster existe
		service.get(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "cluster não encontrado"));

		long logCount = logRepository.countByClusterId(id);
		long metricCount = metricRepository.countByClusterId(id);

		return ResponseEntity.ok(java.util.Map.of(
				"clusterId", id,
				"logCount", logCount,
				"metricCount", metricCount));
	}

	private String resolveOwnerName(UUID ownerId) {
		if (ownerId == null) {
			return null;
		}
		return userRepository.findById(ownerId)
				.map(User::getUsername)
				.orElse(null);
	}
}
