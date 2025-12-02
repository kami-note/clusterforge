package com.kryptforge.clusterforge.webdav;

/**
 * Serviço responsável por gerenciar servidores WebDAV associados aos clusters.
 * O WebDAV compartilha o mesmo volume de dados exposto via FTP, permitindo
 * acesso via clientes que suportam o protocolo HTTP/WebDAV.
 */
public interface WebDavService {

	/**
	 * Cria um servidor WebDAV dedicado para o cluster.
	 *
	 * @param containerName nome base do cluster (usado para nomear o container WebDAV)
	 * @param volumePath    caminho do volume que será exposto
	 * @param hostPort      porta pública (host) alocada dinamicamente
	 * @param username      usuário WebDAV (gera automaticamente se vazio)
	 * @param password      senha WebDAV (gera automaticamente se vazio)
	 * @return informações da instância WebDAV criada
	 */
	WebDavServerInfo createWebDavServer(String containerName,
										String volumePath,
										int hostPort,
										String username,
										String password);

	/**
	 * Remove o container WebDAV associado ao cluster.
	 *
	 * @param webDavContainerId id do container WebDAV
	 */
	void removeWebDavServer(String webDavContainerId);

	/**
	 * Verifica se o WebDAV está rodando.
	 *
	 * @param webDavContainerId id do container WebDAV
	 * @return true quando o container estiver em execução
	 */
	boolean isWebDavServerRunning(String webDavContainerId);

	/**
	 * Registro com os metadados do servidor WebDAV.
	 */
	record WebDavServerInfo(
		String containerId,
		int hostPort,
		String username,
		String password,
		String volumePath
	) {}
}



