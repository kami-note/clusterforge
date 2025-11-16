package com.kryptforge.clusterforge.docker;

import java.util.List;
import java.util.Map;

import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Image;

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
	String createContainer(String image,
						   List<String> command,
						   Map<String, String> environment,
						   List<String> portBindings,
						   List<String> bindMounts,
						   String name);
	void startContainer(String containerId);
	void stopContainer(String containerId, int timeoutSeconds);
	void removeContainer(String containerId, boolean force, boolean removeVolumes);
	InspectContainerResponse inspectContainer(String containerId);

	// Execução e logs
	String getContainerLogs(String containerId, boolean stdout, boolean stderr, Integer tailLines, Integer sinceSeconds);
	String execInContainer(String containerId, List<String> command, boolean attachStdout, boolean attachStderr);
}



