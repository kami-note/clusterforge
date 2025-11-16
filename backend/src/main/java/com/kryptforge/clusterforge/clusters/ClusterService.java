package com.kryptforge.clusterforge.clusters;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClusterService {

	ClusterInstance create(String name, String templateName, ClusterParams params);

	List<ClusterInstance> list();

	Optional<ClusterInstance> get(UUID id);

	ClusterInstance updateStatus(UUID id, ClusterStatus status);

	ClusterInstance updateParams(UUID id, ClusterParams params);

	ClusterInstance updateContainerId(UUID id, String containerId);

	/**
	 * Remove o container Docker e o registro do banco de dados.
	 * @param id ID da instância
	 */
	void delete(UUID id);

	/**
	 * Remove apenas o container Docker (mantém registro no banco).
	 * @param id ID da instância
	 */
	void deleteContainer(UUID id);

	/**
	 * Remove apenas do banco de dados (não remove container Docker).
	 * @param id ID da instância
	 */
	void deleteFromDatabase(UUID id);

	record ClusterParams(
		java.util.Map<String,String> env,
		java.util.List<Integer> ports,
		java.util.List<String> volumes
	) {}
}


