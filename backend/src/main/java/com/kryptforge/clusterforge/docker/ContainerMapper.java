package com.kryptforge.clusterforge.docker;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.ContainerPort;
import com.github.dockerjava.api.model.ContainerNetwork;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.Statistics;
import com.kryptforge.clusterforge.docker.dto.ContainerSummary;
import com.kryptforge.clusterforge.docker.dto.ContainerDetail;
import com.kryptforge.clusterforge.docker.dto.ContainerStats;

/**
 * Mapper dedicado para converter modelos do docker-java em DTOs estáveis.
 */
public final class ContainerMapper {

	private static final Logger log = LoggerFactory.getLogger(ContainerMapper.class);

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

	public static ContainerStats toStats(String containerId, Statistics s) {
		// CPU %
		double cpuPercent = 0.0d;
		try {
			Long cpuDelta = delta(s.getCpuStats() != null ? s.getCpuStats().getCpuUsage().getTotalUsage() : null,
				s.getPreCpuStats() != null ? s.getPreCpuStats().getCpuUsage().getTotalUsage() : null);
			Long systemDelta = delta(s.getCpuStats() != null ? s.getCpuStats().getSystemCpuUsage() : null,
				s.getPreCpuStats() != null ? s.getPreCpuStats().getSystemCpuUsage() : null);
			int cpuCount = 1;
			if (s.getCpuStats() != null && s.getCpuStats().getOnlineCpus() != null) {
				cpuCount = s.getCpuStats().getOnlineCpus().intValue();
			} else if (s.getCpuStats() != null && s.getCpuStats().getCpuUsage() != null && s.getCpuStats().getCpuUsage().getPercpuUsage() != null) {
				cpuCount = s.getCpuStats().getCpuUsage().getPercpuUsage().size();
			}
			if (cpuDelta > 0 && systemDelta > 0) {
				cpuPercent = (cpuDelta.doubleValue() / systemDelta.doubleValue()) * cpuCount * 100.0d;
			}
		} catch (Exception e) {
			log.trace("Erro ao calcular CPU percent para container {}: {}", containerId, e.getMessage());
			cpuPercent = 0.0d;
		}

		// Memória
		long memUsage = 0L;
		long memLimit = 0L;
		double memPercent = 0.0d;
		if (s.getMemoryStats() != null) {
			if (s.getMemoryStats().getUsage() != null) memUsage = s.getMemoryStats().getUsage();
			if (s.getMemoryStats().getLimit() != null) memLimit = s.getMemoryStats().getLimit();
			if (memLimit > 0) {
				memPercent = (memUsage * 100.0d) / memLimit;
			}
		}

		// Rede (somatório de interfaces)
		long rx = 0L, tx = 0L;
		if (s.getNetworks() != null) {
			for (var e : s.getNetworks().entrySet()) {
				if (e.getValue() != null) {
					if (e.getValue().getRxBytes() != null) rx += e.getValue().getRxBytes();
					if (e.getValue().getTxBytes() != null) tx += e.getValue().getTxBytes();
				}
			}
		}

		// Blocos (read/write)
		long blkRead = 0L, blkWrite = 0L;
		if (s.getBlkioStats() != null && s.getBlkioStats().getIoServiceBytesRecursive() != null) {
			var list = s.getBlkioStats().getIoServiceBytesRecursive();
			for (var entry : list) {
				if (entry == null || entry.getOp() == null || entry.getValue() == null) continue;
				String op = entry.getOp();
				if ("Read".equalsIgnoreCase(op)) blkRead += entry.getValue();
				else if ("Write".equalsIgnoreCase(op)) blkWrite += entry.getValue();
			}
		}

		// PIDs
		long pids = 0L;
		if (s.getPidsStats() != null && s.getPidsStats().getCurrent() != null) {
			pids = s.getPidsStats().getCurrent();
		}

		return new ContainerStats(
			containerId,
			s.getRead(),
			round2(cpuPercent),
			memUsage,
			memLimit,
			round2(memPercent),
			rx, tx,
			blkRead, blkWrite,
			pids
		);
	}

	private static long delta(Long now, Long prev) {
		if (now == null || prev == null) return 0L;
		return Math.max(0L, now - prev);
	}

	private static double round2(double v) {
		return Math.round(v * 100.0d) / 100.0d;
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


