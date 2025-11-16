package com.kryptforge.clusterforge.docker;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.Map;

import org.springframework.util.StringUtils;

import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.ContainerPort;
import com.github.dockerjava.api.model.ContainerNetwork;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.kryptforge.clusterforge.docker.dto.ContainerSummary;
import com.kryptforge.clusterforge.docker.dto.ContainerDetail;

/**
 * Mapper dedicado para converter modelos do docker-java em DTOs estáveis.
 */
public final class ContainerMapper {

	private ContainerMapper() {}

	public static ContainerSummary toSummary(Container c) {
		List<String> names = c.getNames() != null ? Arrays.asList(c.getNames()) : List.of();
		List<String> ports = formatPorts(c.getPorts());
		return new ContainerSummary(
			c.getId(),
			names,
			c.getImage(),
			c.getState(),
			c.getStatus(),
			c.getCreated(),
			ports
		);
	}

	public static ContainerDetail toDetail(InspectContainerResponse resp) {
		List<String> names = resp.getName() != null ? List.of(resp.getName()) : List.of();
		List<String> mounts = resp.getMounts() != null
			? resp.getMounts().stream()
				.map(m -> m.getSource() + ":" + m.getDestination())
				.toList()
			: List.of();
		List<String> networks = resp.getNetworkSettings() != null && resp.getNetworkSettings().getNetworks() != null
			? resp.getNetworkSettings().getNetworks().entrySet().stream()
				.map(e -> {
					ContainerNetwork n = e.getValue();
					String ip = n.getIpAddress();
					return e.getKey() + (ip != null && !ip.isBlank() ? "("+ip+")" : "");
				})
				.toList()
			: List.of();
		List<String> ports = List.of();
		if (resp.getNetworkSettings() != null
			&& resp.getNetworkSettings().getPorts() != null
			&& resp.getNetworkSettings().getPorts().getBindings() != null) {
			var bindings = resp.getNetworkSettings().getPorts().getBindings();
			ports = bindings.entrySet().stream()
				.flatMap(e -> {
					var key = e.getKey(); // ExposedPort
					var binds = e.getValue(); // Ports.Binding[]
					if (binds == null) return Stream.<String>empty();
					return Arrays.stream(binds).map(b -> {
						String hostIp = (b.getHostIp() != null && !b.getHostIp().isBlank()) ? b.getHostIp() : "0.0.0.0";
						return hostIp + ":" + b.getHostPortSpec() + "->" + key.getPort() + "/" + key.getProtocol();
					});
				})
				.toList();
		}
		return new ContainerDetail(
			resp.getId(),
			names,
			resp.getConfig() != null ? resp.getConfig().getImage() : null,
			resp.getImageId(),
			resp.getCreated(),
			resp.getState() != null ? resp.getState().getStatus() : null,
			resp.getState() != null ? resp.getState().getStatus() : null,
			resp.getConfig() != null && resp.getConfig().getLabels() != null ? resp.getConfig().getLabels() : Map.of(),
			mounts,
			networks,
			ports
		);
	}

	private static List<String> formatPorts(ContainerPort[] ports) {
		if (ports == null || ports.length == 0) return List.of();
		return Arrays.stream(ports)
			.map(p -> {
				String ip = StringUtils.hasText(p.getIp()) ? p.getIp() : "0.0.0.0";
				Integer privatePort = p.getPrivatePort();
				Integer publicPort = p.getPublicPort();
				String type = p.getType();
				if (publicPort != null) {
					return ip + ":" + publicPort + "->" + privatePort + "/" + type;
				}
				return privatePort + "/" + type;
			})
			.collect(Collectors.toList());
	}
}


