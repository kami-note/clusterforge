package com.kryptforge.clusterforge.docker;

import java.util.List;
import java.util.Map;

import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Image;
import com.kryptforge.clusterforge.docker.dto.ContainerLogsResponse;

/**
 * Serviço de integração com a Docker Engine.
 * Responsável por operações lógicas sobre imagens e containers.
 * Persistência em banco deve ser feita por repositórios específicos (JPA, etc).
 */
public interface DockerEngineService {

	// Imagens
	void pullImage(String imageReference);
	List<Image> listImages();

	// Containers
	List<Container> listContainers(boolean showAll);
	
	/**
	 * Cria um container Docker com configurações opcionais de limites de recursos.
	 * 
	 * @param image nome da imagem Docker
	 * @param command comando a ser executado no container (opcional)
	 * @param environment variáveis de ambiente (opcional)
	 * @param portBindings mapeamento de portas no formato "hostPort:containerPort" (opcional)
	 * @param bindMounts montagens de volumes no formato "hostPath:containerPath" (opcional)
	 * @param name nome do container (opcional)
	 * @param workingDir diretório de trabalho (opcional)
	 * @param stdinOpen se stdin deve estar aberto (opcional)
	 * @param tty se deve alocar um pseudo-TTY (opcional)
	 * @param restart política de restart (opcional: "no", "always", "on-failure", "unless-stopped")
	 * @param cpuLimitPercent limite de CPU em percentual (1-100). Se null ou <= 0, não aplica limite.
	 *                       Valores > 100 são rejeitados.
	 * @param memoryLimitMb limite de memória em megabytes (1-32768). Se null ou <= 0, não aplica limite.
	 *                      Valores > 32768 (32 GB) são rejeitados.
	 * @return ID do container criado
	 * @throws IllegalArgumentException se os limites de recursos forem inválidos
	 */
	String createContainer(String image,
						   List<String> command,
						   Map<String, String> environment,
						   List<String> portBindings,
						   List<String> bindMounts,
						   String name,
						   String workingDir,
						   Boolean stdinOpen,
						   Boolean tty,
						   String restart,
						   Integer cpuLimitPercent,
						   Long memoryLimitMb);
	void startContainer(String containerId);
	void stopContainer(String containerId, int timeoutSeconds);
	void removeContainer(String containerId, boolean force, boolean removeVolumes);
	InspectContainerResponse inspectContainer(String containerId);

	// Execução e logs
	ContainerLogsResponse getContainerLogs(String containerId, boolean stdout, boolean stderr, Integer tailLines, Integer sinceSeconds);
	String execInContainer(String containerId, List<String> command, boolean attachStdout, boolean attachStderr);
}



