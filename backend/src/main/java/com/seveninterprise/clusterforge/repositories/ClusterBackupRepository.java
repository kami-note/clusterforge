package com.seveninterprise.clusterforge.repositories;

import com.seveninterprise.clusterforge.model.ClusterBackup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repositório para gerenciamento de backups de clusters
 */
@Repository
public interface ClusterBackupRepository extends JpaRepository<ClusterBackup, Long> {
    
    /**
     * Busca backups de um cluster ordenados por data de criação (mais recente primeiro)
     */
    List<ClusterBackup> findByClusterIdOrderByCreatedAtDesc(Long clusterId);
    
    /**
     * Busca todos os backups ordenados por data de criação (mais recente primeiro)
     */
    List<ClusterBackup> findAllByOrderByCreatedAtDesc();
    
    /**
     * Busca backups que expiraram antes de uma data específica
     */
    List<ClusterBackup> findByExpiresAtBefore(LocalDateTime cutoffTime);
    
    /**
     * Busca backups com status específico
     */
    List<ClusterBackup> findByStatus(ClusterBackup.BackupStatus status);
    
    /**
     * Busca backups que expiraram ou têm status específico
     */
    List<ClusterBackup> findByExpiresAtBeforeOrStatus(LocalDateTime cutoffTime, ClusterBackup.BackupStatus status);
    
    /**
     * Busca backups automáticos
     */
    List<ClusterBackup> findByAutomaticTrue();
    
    /**
     * Busca backups manuais
     */
    List<ClusterBackup> findByAutomaticFalse();
    
    /**
     * Busca backups por tipo
     */
    List<ClusterBackup> findByBackupType(ClusterBackup.BackupType backupType);
    
    /**
     * Busca backups criados em um período específico
     */
    List<ClusterBackup> findByCreatedAtBetween(LocalDateTime startTime, LocalDateTime endTime);
    
    /**
     * Busca último backup de um cluster
     */
    Optional<ClusterBackup> findTopByClusterIdOrderByCreatedAtDesc(Long clusterId);
    
    /**
     * Busca backups que precisam de verificação de integridade
     */
    @Query("SELECT b FROM ClusterBackup b WHERE " +
           "b.status = 'COMPLETED' AND " +
           "(b.checksum IS NULL OR b.lastRestoreAt > b.completedAt)")
    List<ClusterBackup> findBackupsNeedingIntegrityCheck();
    
    /**
     * Busca backups com maior número de restaurações
     */
    @Query("SELECT b FROM ClusterBackup b WHERE b.restoreCount > 0 ORDER BY b.restoreCount DESC")
    List<ClusterBackup> findMostRestoredBackups();
    
    /**
     * Calcula estatísticas de backup por cluster
     */
    @Query("SELECT b.cluster.id, COUNT(b), SUM(b.backupSizeBytes), AVG(b.backupSizeBytes) " +
           "FROM ClusterBackup b WHERE b.status = 'COMPLETED' GROUP BY b.cluster.id")
    List<Object[]> getBackupStatsByCluster();
    
    /**
     * Busca backups com tamanho acima de um threshold
     */
    @Query("SELECT b FROM ClusterBackup b WHERE b.backupSizeBytes > :threshold ORDER BY b.backupSizeBytes DESC")
    List<ClusterBackup> findLargeBackups(@Param("threshold") Long threshold);
    
    /**
     * Busca backups com taxa de compressão baixa
     */
    @Query("SELECT b FROM ClusterBackup b WHERE b.compressionRatio < :threshold ORDER BY b.compressionRatio ASC")
    List<ClusterBackup> findLowCompressionBackups(@Param("threshold") Double threshold);
    
    /**
     * Busca backups que falharam recentemente
     */
    @Query("SELECT b FROM ClusterBackup b WHERE " +
           "b.status = 'FAILED' AND " +
           "b.createdAt >= :since " +
           "ORDER BY b.createdAt DESC")
    List<ClusterBackup> findRecentFailedBackups(@Param("since") LocalDateTime since);
    
    /**
     * Conta backups por status
     */
    @Query("SELECT b.status, COUNT(b) FROM ClusterBackup b GROUP BY b.status")
    List<Object[]> countBackupsByStatus();
    
    /**
     * Conta backups por tipo
     */
    @Query("SELECT b.backupType, COUNT(b) FROM ClusterBackup b GROUP BY b.backupType")
    List<Object[]> countBackupsByType();
    
    /**
     * Busca backups que precisam ser limpos (excedem limite de retenção)
     */
    @Query("SELECT b FROM ClusterBackup b WHERE " +
           "b.expiresAt < :now AND " +
           "b.status IN ('COMPLETED', 'FAILED') " +
           "ORDER BY b.expiresAt ASC")
    List<ClusterBackup> findBackupsForCleanup(@Param("now") LocalDateTime now);
    
    /**
     * Busca clusters com muitos backups (para limpeza)
     */
    @Query("SELECT b.cluster.id, COUNT(b) FROM ClusterBackup b " +
           "WHERE b.status = 'COMPLETED' " +
           "GROUP BY b.cluster.id " +
           "HAVING COUNT(b) > :maxBackups")
    List<Object[]> findClustersWithTooManyBackups(@Param("maxBackups") int maxBackups);
    
    /**
     * Busca último backup bem-sucedido de cada cluster
     */
    @Query("SELECT b FROM ClusterBackup b WHERE " +
           "b.status = 'COMPLETED' AND " +
           "b.id IN (SELECT MAX(b2.id) FROM ClusterBackup b2 WHERE b2.cluster.id = b.cluster.id)")
    List<ClusterBackup> findLatestSuccessfulBackups();
    
    /**
     * Busca backups que nunca foram restaurados
     */
    @Query("SELECT b FROM ClusterBackup b WHERE b.restoreCount = 0 AND b.status = 'COMPLETED'")
    List<ClusterBackup> findUnusedBackups();
    
    /**
     * Calcula tamanho total de backups por cluster
     */
    @Query("SELECT b.cluster.id, SUM(b.backupSizeBytes) FROM ClusterBackup b " +
           "WHERE b.status = 'COMPLETED' AND b.backupSizeBytes IS NOT NULL " +
           "GROUP BY b.cluster.id")
    List<Object[]> getTotalBackupSizeByCluster();

    /**
     * Remove backups por ID do cluster
     */
    void deleteByClusterId(Long clusterId);
}
