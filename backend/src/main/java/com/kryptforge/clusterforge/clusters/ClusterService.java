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

	void delete(UUID id);

	record ClusterParams(
		java.util.Map<String,String> env,
		java.util.List<Integer> ports,
		java.util.List<String> volumes
	) {}
}


