package com.kryptforge.clusterforge.web;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.kryptforge.clusterforge.clusters.ClusterInstance;
import com.kryptforge.clusterforge.clusters.ClusterService;
import com.kryptforge.clusterforge.clusters.ClusterStatus;
import com.kryptforge.clusterforge.clusters.dto.ClusterDtos.ClusterResponse;
import com.kryptforge.clusterforge.clusters.dto.ClusterDtos.ClusterStatusUpdateRequest;
import com.kryptforge.clusterforge.clusters.dto.ClusterDtos.ClusterUpdateParamsRequest;
import com.kryptforge.clusterforge.clusters.dto.ClusterDtos.ClusterOwnerUpdateRequest;
import com.kryptforge.clusterforge.docker.DockerEngineService;
import com.kryptforge.clusterforge.users.UserRepository;
import com.kryptforge.clusterforge.users.User;

@RestController
@RequestMapping(path = "/api/clusters", produces = MediaType.APPLICATION_JSON_VALUE)
public class ClusterController {

	private final ClusterService service;
	private final DockerEngineService dockerEngineService;
	private final UserRepository userRepository;

	public ClusterController(ClusterService service, DockerEngineService dockerEngineService, UserRepository userRepository) {
		this.service = service;
		this.dockerEngineService = dockerEngineService;
		this.userRepository = userRepository;
	}

	// POST /api/clusters removido - use POST /api/templates/{name}/instantiate para criar clusters
	// A instanciação de template já cria o container Docker e persiste no banco

	@GetMapping
	public List<ClusterResponse> list() {
		org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ClusterController.class);
		
		return service.list().stream()
			// Filtra apenas instâncias que têm containerId (têm container Docker)
			.filter(instance -> instance.getContainerId() != null && !instance.getContainerId().isBlank())
			.map(instance -> {
				try {
					// Busca status real do Docker
					ClusterStatus dockerStatus = getDockerStatus(instance.getContainerId());
					
					// Se container foi deletado, filtra da listagem (não mostra)
					// O syncStatus será feito de forma assíncrona ou em outra operação
					if (dockerStatus == ClusterStatus.DELETED) {
						// Loga para sincronização posterior (opcional)
						log.debug("Container {} deletado, filtrando da listagem", instance.getContainerId());
						return null; // Filtra da listagem
					}
					
					// Cria resposta com status do Docker ao invés do banco
					return ClusterResponse.from(instance, dockerStatus, resolveOwnerName(instance.getOwnerId()));
				} catch (Exception e) {
					// Em caso de erro ao buscar status, loga e retorna com status do banco
					// Não quebra a listagem inteira por causa de um container problemático
					log.warn("Erro ao obter status do Docker para container {}: {}", 
						instance.getContainerId(), e.getMessage());
					return ClusterResponse.from(instance, instance.getStatus(), resolveOwnerName(instance.getOwnerId()));
				}
			})
			.filter(response -> response != null) // Remove containers deletados
			.toList();
	}

	/**
	 * Obtém o status real do container Docker e mapeia para ClusterStatus.
	 * @param containerId ID do container Docker
	 * @return status mapeado do Docker ou DELETED se container não existir
	 */
	private ClusterStatus getDockerStatus(String containerId) {
		try {
			var inspect = dockerEngineService.inspectContainer(containerId);
			if (inspect != null && inspect.getState() != null) {
				String dockerStatus = inspect.getState().getStatus();
				Boolean running = inspect.getState().getRunning();
				
				if (running != null && running) {
					return ClusterStatus.ACTIVE;
				} else if ("exited".equalsIgnoreCase(dockerStatus) || "stopped".equalsIgnoreCase(dockerStatus)) {
					return ClusterStatus.STOPPED;
				} else if ("created".equalsIgnoreCase(dockerStatus)) {
					return ClusterStatus.PENDING;
				} else {
					// Outros estados (restarting, removing, etc)
					return ClusterStatus.ERROR;
				}
			}
		} catch (com.github.dockerjava.api.exception.NotFoundException e) {
			// Container não existe mais
			return ClusterStatus.DELETED;
		} catch (Exception e) {
			// Erro ao inspecionar container
			return ClusterStatus.ERROR;
		}
		return ClusterStatus.ERROR;
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

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable("id") UUID id) {
		service.delete(id);
		return ResponseEntity.noContent().build();
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


