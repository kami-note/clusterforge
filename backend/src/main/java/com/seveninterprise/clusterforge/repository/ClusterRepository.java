package com.seveninterprise.clusterforge.repository;

import com.seveninterprise.clusterforge.model.Cluster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
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
    
    /**
     * Busca todos os clusters SEM carregar o relacionamento User (mais rápido)
     * Usa fetch join apenas para evitar lazy loading
     */
    @Query("SELECT c FROM Cluster c LEFT JOIN FETCH c.user")
    List<Cluster> findAllWithUser();
}



