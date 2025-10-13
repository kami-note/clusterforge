package com.seveninterprise.clusterforge.repository;

import com.seveninterprise.clusterforge.model.Cluster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClusterRepository extends JpaRepository<Cluster, Long> {
    
    List<Cluster> findByUserId(Long userId);
    
    Optional<Cluster> findByName(String name);
    
    List<Cluster> findByPort(int port);
    
    boolean existsByName(String name);
    
    boolean existsByPort(int port);
}



