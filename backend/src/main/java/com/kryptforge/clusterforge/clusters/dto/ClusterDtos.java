package com.kryptforge.clusterforge.clusters.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.kryptforge.clusterforge.clusters.ClusterInstance;
import com.kryptforge.clusterforge.clusters.ClusterStatus;
import com.kryptforge.clusterforge.clusters.ClusterService.ClusterParams;

public final class ClusterDtos {
	private ClusterDtos() {}

	public record ClusterCreateRequest(
		String name,
		String templateName,
		Map<String,String> env,
		List<Integer> ports,
		List<String> volumes
	) {
		public ClusterParams toParams() {
			return new ClusterParams(env, ports, volumes);
		}
	}

	public record ClusterUpdateParamsRequest(
		Map<String,String> env,
		List<Integer> ports,
		List<String> volumes
	) {
		public ClusterParams toParams() {
			return new ClusterParams(env, ports, volumes);
		}
	}

	public record ClusterStatusUpdateRequest(
		ClusterStatus status
	) {}

	public record ClusterResponse(
		UUID id,
		String name,
		String templateName,
		ClusterStatus status,
		Instant createdAt,
		Instant updatedAt,
		Map<String,String> env,
		List<Integer> ports,
		List<String> volumes,
		String containerId,
		AccessInfo ftp,
		AccessInfo webDav
	) {
		public static ClusterResponse from(ClusterInstance c) {
			return from(c, c.getStatus());
		}

		public static ClusterResponse from(ClusterInstance c, ClusterStatus status) {
			return new ClusterResponse(
				c.getId(),
				c.getName(),
				c.getTemplateName(),
				status,
				c.getCreatedAt(),
				c.getUpdatedAt(),
				c.getEnv(),
				c.getPorts(),
				c.getVolumes(),
				c.getContainerId(),
				new AccessInfo(c.getFtpContainerId(), c.getFtpPort(), c.getFtpUser(), c.getFtpPassword()),
				new AccessInfo(c.getWebDavContainerId(), c.getWebDavPort(), c.getWebDavUser(), c.getWebDavPassword())
			);
		}
	}

	public record AccessInfo(
		String containerId,
		Integer port,
		String username,
		String password
	) {}
}


