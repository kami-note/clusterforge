package com.kryptforge.clusterforge.ftp;

/**
 * Serviço responsável por gerenciar servidores FTP para containers.
 * Cada container terá seu próprio servidor FTP atrelado de forma permanente.
 * O servidor FTP é removido automaticamente quando o container principal é removido.
 */
public interface FtpService {

	/**
	 * Cria um servidor FTP para um container específico.
	 * O servidor FTP compartilha o volume do container principal e está atrelado a ele.
	 * O servidor FTP será removido automaticamente quando o container principal for removido.
	 * 
	 * @param containerName nome do container principal (usado para nomear o servidor FTP)
	 * @param volumePath caminho do volume do container principal a ser compartilhado
	 * @param ftpPort porta do host para o servidor FTP (alocada dinamicamente)
	 * @param ftpUser usuário FTP (padrão: ftpuser)
	 * @param ftpPassword senha FTP (gerada automaticamente se não fornecida)
	 * @return informações do servidor FTP criado
	 */
	FtpServerInfo createFtpServer(String containerName, String volumePath, int ftpPort, String ftpUser, String ftpPassword);

	/**
	 * Remove um servidor FTP.
	 * Este método é chamado automaticamente quando o container principal associado é removido.
	 * 
	 * @param ftpContainerId ID do container FTP
	 */
	void removeFtpServer(String ftpContainerId);

	/**
	 * Verifica se um servidor FTP está rodando.
	 * 
	 * @param ftpContainerId ID do container FTP
	 * @return true se estiver rodando, false caso contrário
	 */
	boolean isFtpServerRunning(String ftpContainerId);

	/**
	 * Informações do servidor FTP criado.
	 */
	record FtpServerInfo(
		String containerId,
		int hostPort,
		String ftpUser,
		String ftpPassword,
		String volumePath
	) {}
}

