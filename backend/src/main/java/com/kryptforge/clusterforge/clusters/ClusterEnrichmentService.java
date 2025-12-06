package com.kryptforge.clusterforge.clusters;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.github.dockerjava.api.exception.NotFoundException;
import com.kryptforge.clusterforge.clusters.dto.ClusterResponse;
import com.kryptforge.clusterforge.docker.DockerEngineService;
import com.kryptforge.clusterforge.docker.util.DockerStatusMapper;
import com.kryptforge.clusterforge.users.User;
import com.kryptforge.clusterforge.users.UserRepository;

import jakarta.annotation.PreDestroy;

/**
 * Serviço responsável por enriquecer dados de clusters com informações do
 * Docker.
 * 
 * <p>
 * Extraído do ClusterController para seguir o princípio de responsabilidade
 * única.
 * </p>
 * 
 * <p>
 * Responsabilidades:
 * </p>
 * <ul>
 * <li>Buscar status real dos containers no Docker</li>
 * <li>Enriquecer lista de clusters em paralelo</li>
 * <li>Resolver nomes de proprietários</li>
 * <li>Tratar erros de forma resiliente</li>
 * </ul>
 */
@Service
public class ClusterEnrichmentService {

    private static final Logger log = LoggerFactory.getLogger(ClusterEnrichmentService.class);
    private static final int THREAD_POOL_SIZE = 10;
    private static final long TIMEOUT_SECONDS = 30;

    private final DockerEngineService dockerEngineService;
    private final UserRepository userRepository;
    private final ExecutorService executorService;

    public ClusterEnrichmentService(
            DockerEngineService dockerEngineService,
            UserRepository userRepository) {
        this.dockerEngineService = dockerEngineService;
        this.userRepository = userRepository;
        this.executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE, r -> {
            Thread thread = new Thread(r, "cluster-enrichment-pool");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * Enriquece uma lista de clusters com status real do Docker.
     * Executa em paralelo usando ExecutorService dedicado.
     * 
     * @param instances lista de instâncias de cluster
     * @return lista de ClusterResponse com status atualizado (containers deletados
     *         são filtrados)
     */
    public List<ClusterResponse> enrichClusters(List<ClusterInstance> instances) {
        if (instances == null || instances.isEmpty()) {
            return List.of();
        }

        List<CompletableFuture<ClusterResponse>> futures = instances.stream()
                .map(instance -> CompletableFuture.supplyAsync(
                        () -> enrichWithDockerStatus(instance),
                        executorService).exceptionally(ex -> {
                            log.warn("Erro ao enriquecer cluster {}: {}",
                                    instance.getId(), ex.getMessage());
                            // Em caso de erro, retorna com status do banco
                            return ClusterResponse.from(
                                    instance,
                                    instance.getStatus(),
                                    resolveOwnerName(instance.getOwnerId()));
                        }))
                .toList();

        return futures.stream()
                .map(future -> {
                    try {
                        return future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                    } catch (Exception e) {
                        log.warn("Timeout ao enriquecer cluster: {}", e.getMessage());
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * Enriquece uma lista de clusters filtrando por owner ID.
     * 
     * @param instances lista de instâncias de cluster
     * @param ownerId   ID do proprietário para filtrar
     * @return lista filtrada e enriquecida de ClusterResponse
     */
    public List<ClusterResponse> enrichClustersByOwner(List<ClusterInstance> instances, UUID ownerId) {
        if (instances == null || instances.isEmpty() || ownerId == null) {
            return List.of();
        }

        List<ClusterInstance> filtered = instances.stream()
                .filter(instance -> ownerId.equals(instance.getOwnerId()))
                .toList();

        return enrichClusters(filtered);
    }

    /**
     * Enriquece um único cluster com status real do Docker.
     * 
     * @param instance instância do cluster
     * @return ClusterResponse com status atualizado
     */
    public ClusterResponse enrichSingleCluster(ClusterInstance instance) {
        if (instance == null) {
            return null;
        }
        return enrichWithDockerStatus(instance);
    }

    /**
     * Obtém o status real do container Docker e mapeia para ClusterStatus.
     * 
     * @param containerId ID do container Docker
     * @return status mapeado do Docker ou DELETED se container não existir
     */
    public ClusterStatus getDockerStatus(String containerId) {
        if (containerId == null || containerId.isBlank()) {
            return ClusterStatus.ERROR;
        }

        try {
            var inspect = dockerEngineService.inspectContainer(containerId);
            if (inspect != null && inspect.getState() != null) {
                return DockerStatusMapper.fromContainerState(inspect.getState());
            }
        } catch (NotFoundException e) {
            return ClusterStatus.DELETED;
        } catch (Exception e) {
            log.debug("Erro ao obter status do Docker para container {}: {}",
                    containerId, e.getMessage());
            return ClusterStatus.ERROR;
        }
        return ClusterStatus.ERROR;
    }

    /**
     * Resolve o nome de um proprietário a partir do seu ID.
     * 
     * @param ownerId ID do proprietário
     * @return nome do usuário ou null se não encontrado
     */
    public String resolveOwnerName(UUID ownerId) {
        if (ownerId == null) {
            return null;
        }
        return userRepository.findById(ownerId)
                .map(User::getUsername)
                .orElse(null);
    }

    /**
     * Enriquece uma instância com status real do Docker.
     * 
     * @param instance Instância do cluster
     * @return ClusterResponse com status atualizado ou null se container foi
     *         deletado
     */
    private ClusterResponse enrichWithDockerStatus(ClusterInstance instance) {
        String ownerName = resolveOwnerName(instance.getOwnerId());

        if (instance.getContainerId() == null || instance.getContainerId().isBlank()) {
            return ClusterResponse.from(instance, instance.getStatus(), ownerName);
        }

        try {
            ClusterStatus dockerStatus = getDockerStatus(instance.getContainerId());

            if (dockerStatus == ClusterStatus.DELETED) {
                log.debug("Container {} deletado, filtrando da listagem",
                        instance.getContainerId());
                return null;
            }

            return ClusterResponse.from(instance, dockerStatus, ownerName);
        } catch (Exception e) {
            log.warn("Erro ao obter status do Docker para container {}: {}",
                    instance.getContainerId(), e.getMessage());
            return ClusterResponse.from(instance, instance.getStatus(), ownerName);
        }
    }

    /**
     * Encerra o ExecutorService quando o bean for destruído.
     */
    @PreDestroy
    public void shutdown() {
        log.info("Encerrando ClusterEnrichmentService ExecutorService...");
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
