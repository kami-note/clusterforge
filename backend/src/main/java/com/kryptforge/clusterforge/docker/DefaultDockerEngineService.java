package com.kryptforge.clusterforge.docker;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
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
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.api.model.Ports.Binding;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.InternetProtocol;
import com.github.dockerjava.api.model.RestartPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kryptforge.clusterforge.docker.dto.ContainerLogsResponse;
import com.kryptforge.clusterforge.docker.util.ContainerLogParser;

@Service
public class DefaultDockerEngineService implements DockerEngineService {

	private final DockerClient dockerClient;
	private static final Logger log = LoggerFactory.getLogger(DefaultDockerEngineService.class);

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
								  List<String> portBindings,
								  List<String> bindMounts,
								  String name,
								  String workingDir,
								  Boolean stdinOpen,
								  Boolean tty,
								  String restart) {
		requireText(image, "image");

		List<Bind> binds = new ArrayList<>();
		if (!CollectionUtils.isEmpty(bindMounts)) {
			for (String spec : bindMounts) {
				if (!StringUtils.hasText(spec)) continue;
				String[] parts = spec.split(":");
				if (parts.length < 2) continue;
				// parts[0] = hostPath, parts[1] = containerPath
				binds.add(Bind.parse(spec));
			}
		}

		Ports ports = new Ports();
		if (!CollectionUtils.isEmpty(portBindings)) {
			for (String pb : portBindings) {
				if (!StringUtils.hasText(pb)) continue;
				// formatos aceitos: "8080:80", "127.0.0.1:8080:80", "8080:80/tcp", "8080:80/udp"
				String spec = pb.trim();
				String proto = "tcp";
				int slashIdx = spec.indexOf('/');
				if (slashIdx > 0) {
					proto = spec.substring(slashIdx + 1).trim();
					spec = spec.substring(0, slashIdx);
				}
				String[] parts = spec.split(":");
				try {
					if (parts.length == 2) {
						int hostPort = Integer.parseInt(parts[0]);
						int containerPort = Integer.parseInt(parts[1]);
						InternetProtocol ip = "udp".equalsIgnoreCase(proto) ? InternetProtocol.UDP : InternetProtocol.TCP;
						ExposedPort ep = new ExposedPort(containerPort, ip);
						ports.bind(ep, Binding.bindPort(hostPort));
					} else if (parts.length == 3) {
						// hostIp : hostPort : containerPort
						String hostIp = parts[0];
						int hostPort = Integer.parseInt(parts[1]);
						int containerPort = Integer.parseInt(parts[2]);
						InternetProtocol ip = "udp".equalsIgnoreCase(proto) ? InternetProtocol.UDP : InternetProtocol.TCP;
						ExposedPort ep = new ExposedPort(containerPort, ip);
						ports.bind(ep, Binding.bindIpAndPort(hostIp, hostPort));
					}
				} catch (Exception e) {
					log.warn("Entrada de port binding inválida e será ignorada: '{}'", pb);
				}
			}
		}

		HostConfig hostConfig = HostConfig.newHostConfig()
			.withBinds(binds)
			.withPortBindings(ports);

		// Configura restart policy se especificado
		if (StringUtils.hasText(restart)) {
			RestartPolicy restartPolicy = parseRestartPolicy(restart);
			if (restartPolicy != null) {
				hostConfig = hostConfig.withRestartPolicy(restartPolicy);
			}
		}

		var createCmd = dockerClient.createContainerCmd(image)
			.withHostConfig(hostConfig);

		// expõe portas (necessário em alguns daemons)
		if (!ports.getBindings().isEmpty()) {
			createCmd.withExposedPorts(ports.getBindings().keySet().toArray(new ExposedPort[0]));
		}

		if (!CollectionUtils.isEmpty(command)) {
			createCmd.withCmd(command);
		}
		if (StringUtils.hasText(name)) {
			createCmd.withName(name);
		}
		if (StringUtils.hasText(workingDir)) {
			createCmd.withWorkingDir(workingDir);
		}
		if (stdinOpen != null) {
			createCmd.withStdinOpen(stdinOpen);
		}
		if (tty != null) {
			createCmd.withTty(tty);
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
	public ContainerLogsResponse getContainerLogs(String containerId,
												  boolean stdout,
												  boolean stderr,
												  Integer tailLines,
												  Integer sinceSeconds) {
		requireText(containerId, "containerId");
		StringBuilder sb = new StringBuilder(4096);
		AtomicReference<Long> lastTimestamp = new AtomicReference<>(null);
		try {
			var cmd = dockerClient.logContainerCmd(containerId)
				.withStdOut(stdout)
				.withStdErr(stderr)
				.withTimestamps(true);
			if (tailLines != null) {
				cmd.withTail(tailLines);
			}
			if (sinceSeconds != null) {
				cmd.withSince(sinceSeconds);
			}
			cmd.exec(new ResultCallback.Adapter<Frame>() {
				@Override
				public void onNext(Frame frame) {
					var event = ContainerLogParser.parseFrame(containerId, frame);
					if (event != null) {
						sb.append(event.getMessage());
						// Atualizar lastTimestamp apenas se o evento tiver timestamp válido
						// (epochSecond sempre existe, mas pode ser baseado em Instant.now() se não houver timestamp no log)
						if (event.getTimestamp() != null) {
							lastTimestamp.set(event.getEpochSecond());
						}
					}
				}
			}).awaitCompletion();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (Exception e) {
			throw new IllegalStateException("Falha ao obter logs do container " + containerId, e);
		}
		return new ContainerLogsResponse(containerId, sb.toString(), lastTimestamp.get());
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

	private static RestartPolicy parseRestartPolicy(String restart) {
		if (restart == null || restart.trim().isEmpty()) {
			return null;
		}
		String r = restart.trim().toLowerCase();
		switch (r) {
			case "no":
			case "false":
				return RestartPolicy.noRestart();
			case "always":
				return RestartPolicy.alwaysRestart();
			case "on-failure":
			case "on_failure":
				return RestartPolicy.onFailureRestart(0);
			case "unless-stopped":
			case "unless_stopped":
				return RestartPolicy.unlessStoppedRestart();
			default:
				log.warn("Política de restart desconhecida: '{}', usando 'no'", restart);
				return RestartPolicy.noRestart();
		}
	}

	private static void requireText(String value, String name) {
		if (!StringUtils.hasText(value)) {
			throw new IllegalArgumentException(name + " é obrigatório");
		}
	}
}



