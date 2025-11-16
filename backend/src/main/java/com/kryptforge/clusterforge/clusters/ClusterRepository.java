package com.kryptforge.clusterforge.clusters;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ClusterRepository extends JpaRepository<ClusterInstance, UUID> {
	Optional<ClusterInstance> findByName(String name);
	boolean existsByName(String name);
}


