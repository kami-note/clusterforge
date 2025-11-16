package com.kryptforge.clusterforge.docker;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Image;
import com.github.dockerjava.api.model.Volume;

@Service
public class DefaultDockerEngineService implements DockerEngineService {

	private final DockerClient dockerClient;

	public DefaultDockerEngineService(DockerConnection connection) {
		this.dockerClient = Objects.requireNonNull(connection, "connection").getClient();
	}

	@Override
	public void pullImage(String imageReference) {
		requireText(imageReference, "imageReference");
		try {
			dockerClient.pullImageCmd(imageReference).start().awaitCompletion();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	@Override
	public List<Image> listImages() {
		return dockerClient.listImagesCmd().exec();
	}

	@Override
	public List<Container> listContainers(boolean showAll) {
		return dockerClient.listContainersCmd().withShowAll(showAll).exec();
	}

	@Override
	public String createContainer(String image,
								  List<String> command,
								  Map<String, String> environment,
								  List<String> bindMounts,
								  String name) {
		requireText(image, "image");

		List<Bind> binds = new ArrayList<>();
		if (!CollectionUtils.isEmpty(bindMounts)) {
			for (String spec : bindMounts) {
				if (!StringUtils.hasText(spec)) continue;
				String[] parts = spec.split(":");
				if (parts.length < 2) continue;
				// parts[0] = hostPath, parts[1] = containerPath
				String containerPath = parts[1];
				binds.add(Bind.parse(spec));
				new Volume(containerPath);
			}
		}

		HostConfig hostConfig = HostConfig.newHostConfig()
			.withBinds(binds);

		var createCmd = dockerClient.createContainerCmd(image)
			.withHostConfig(hostConfig);

		if (!CollectionUtils.isEmpty(command)) {
			createCmd.withCmd(command);
		}
		if (StringUtils.hasText(name)) {
			createCmd.withName(name);
		}
		if (!CollectionUtils.isEmpty(environment)) {
			List<String> envList = environment.entrySet().stream()
				.map(e -> e.getKey() + "=" + e.getValue())
				.collect(Collectors.toList());
			createCmd.withEnv(envList);
		}

		CreateContainerResponse response = createCmd.exec();
		return response.getId();
	}

	@Override
	public void startContainer(String containerId) {
		requireText(containerId, "containerId");
		dockerClient.startContainerCmd(containerId).exec();
	}

	@Override
	public void stopContainer(String containerId, int timeoutSeconds) {
		requireText(containerId, "containerId");
		dockerClient.stopContainerCmd(containerId)
			.withTimeout(Math.max(0, timeoutSeconds))
			.exec();
	}

	@Override
	public void removeContainer(String containerId, boolean force, boolean removeVolumes) {
		requireText(containerId, "containerId");
		dockerClient.removeContainerCmd(containerId)
			.withForce(force)
			.withRemoveVolumes(removeVolumes)
			.exec();
	}

	@Override
	public InspectContainerResponse inspectContainer(String containerId) {
		requireText(containerId, "containerId");
		return dockerClient.inspectContainerCmd(containerId).exec();
	}

	@Override
	public String getContainerLogs(String containerId,
								   boolean stdout,
								   boolean stderr,
								   Integer tailLines,
								   Integer sinceSeconds) {
		requireText(containerId, "containerId");
		StringBuilder sb = new StringBuilder(4096);
		try {
			var cmd = dockerClient.logContainerCmd(containerId)
				.withStdOut(stdout)
				.withStdErr(stderr)
				.withTimestamps(false);
			if (tailLines != null) {
				cmd.withTail(tailLines);
			}
			if (sinceSeconds != null) {
				cmd.withSince(sinceSeconds);
			}
			cmd.exec(new ResultCallback.Adapter<Frame>() {
				@Override
				public void onNext(Frame frame) {
					if (frame != null && frame.getPayload() != null) {
						sb.append(new String(frame.getPayload(), StandardCharsets.UTF_8));
					}
				}
			}).awaitCompletion();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (Exception e) {
			throw new IllegalStateException("Falha ao obter logs do container " + containerId, e);
		}
		return sb.toString();
	}

	@Override
	public String execInContainer(String containerId, List<String> command, boolean attachStdout, boolean attachStderr) {
		requireText(containerId, "containerId");
		if (CollectionUtils.isEmpty(command)) {
			throw new IllegalArgumentException("command não pode ser vazio");
		}
		StringBuilder sb = new StringBuilder(1024);
		try {
			String[] cmdArray = command.toArray(new String[0]);
			var execCreate = dockerClient.execCreateCmd(containerId)
				.withAttachStdout(attachStdout)
				.withAttachStderr(attachStderr)
				.withCmd(cmdArray)
				.exec();

			dockerClient.execStartCmd(execCreate.getId())
				.exec(new ResultCallback.Adapter<Frame>() {
					@Override
					public void onNext(Frame frame) {
						if (frame != null && frame.getPayload() != null) {
							sb.append(new String(frame.getPayload(), StandardCharsets.UTF_8));
						}
					}
				}).awaitCompletion();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (Exception e) {
			throw new IllegalStateException("Falha ao executar comando no container " + containerId, e);
		}
		return sb.toString();
	}

	private static void requireText(String value, String name) {
		if (!StringUtils.hasText(value)) {
			throw new IllegalArgumentException(name + " é obrigatório");
		}
	}
}



