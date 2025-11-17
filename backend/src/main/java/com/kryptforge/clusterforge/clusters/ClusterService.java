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
	 * Atualiza informações do servidor FTP da instância.
	 * @param id ID da instância
	 * @param ftpContainerId ID do container FTP
	 * @param ftpPort porta do host do servidor FTP
	 * @param ftpUser usuário FTP
	 * @param ftpPassword senha FTP
	 * @return instância atualizada
	 */
	ClusterInstance updateFtpInfo(UUID id, String ftpContainerId, Integer ftpPort, String ftpUser, String ftpPassword);

	/**
	 * Sincroniza o status da instância com o estado real do container Docker.
	 * Verifica se o container existe e qual é seu estado atual.
	 * @param id ID da instância
	 * @return instância atualizada com status sincronizado
	 */
	ClusterInstance syncStatus(UUID id);

	/**
	 * Inicia o container Docker e atualiza o status para ACTIVE.
	 * @param id ID da instância
	 * @return instância atualizada
	 */
	ClusterInstance startContainer(UUID id);

	/**
	 * Para o container Docker e atualiza o status para STOPPED.
	 * @param id ID da instância
	 * @param timeoutSeconds timeout em segundos para parar o container
	 * @return instância atualizada
	 */
	ClusterInstance stopContainer(UUID id, int timeoutSeconds);

	/**
	 * Remove o container Docker e o registro do banco de dados.
	 * @param id ID da instância
	 */
	void delete(UUID id);

	/**
	 * Remove apenas o container Docker (mantém registro no banco).
	 * Atualiza o status para DELETED.
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


