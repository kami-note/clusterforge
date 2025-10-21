package com.seveninterprise.clusterforge.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Modelo para backup de clusters
 * 
 * Representa um backup de cluster incluindo:
 * - Metadados do backup (tipo, data, tamanho)
 * - Status e integridade
 * - Configurações de retenção
 * - Informações de restauração
 */
@Entity
@Table(name = "cluster_backups")
public class ClusterBackup {
    
    public enum BackupType {
        FULL,           // Backup completo (código + dados + configuração)
        INCREMENTAL,    // Apenas mudanças desde último backup
        CONFIG_ONLY,    // Apenas configurações
        DATA_ONLY       // Apenas dados da aplicação
    }
    
    public enum BackupStatus {
        IN_PROGRESS,    // Backup em andamento
        COMPLETED,      // Backup concluído com sucesso
        FAILED,         // Backup falhou
        CORRUPTED,      // Backup corrompido
        EXPIRED         // Backup expirado (para remoção)
    }
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "cluster_id", nullable = false)
    private Cluster cluster;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BackupType backupType;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BackupStatus status = BackupStatus.IN_PROGRESS;
    
    @Column(name = "backup_path", nullable = false)
    private String backupPath;
    
    @Column(name = "backup_size_bytes")
    private Long backupSizeBytes;
    
    @Column(name = "compression_ratio")
    private Double compressionRatio;
    
    @Column(name = "description")
    private String description;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
    
    @Column(name = "completed_at")
    private LocalDateTime completedAt;
    
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;
    
    @Column(name = "checksum")
    private String checksum;
    
    @Column(name = "is_automatic")
    private boolean automatic = false;
    
    @Column(name = "restore_count")
    private int restoreCount = 0;
    
    @Column(name = "last_restore_at")
    private LocalDateTime lastRestoreAt;
    
    @Column(name = "error_message")
    private String errorMessage;
    
    // Configurações de backup
    @Column(name = "auto_backup_enabled")
    private boolean autoBackupEnabled = true;
    
    @Column(name = "backup_interval_hours")
    private int backupIntervalHours = 24;
    
    @Column(name = "retention_days")
    private int retentionDays = 30;
    
    @Column(name = "max_backups")
    private int maxBackups = 10;
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public Cluster getCluster() {
        return cluster;
    }
    
    public void setCluster(Cluster cluster) {
        this.cluster = cluster;
    }
    
    public BackupType getBackupType() {
        return backupType;
    }
    
    public void setBackupType(BackupType backupType) {
        this.backupType = backupType;
    }
    
    public BackupStatus getStatus() {
        return status;
    }
    
    public void setStatus(BackupStatus status) {
        this.status = status;
    }
    
    public String getBackupPath() {
        return backupPath;
    }
    
    public void setBackupPath(String backupPath) {
        this.backupPath = backupPath;
    }
    
    public Long getBackupSizeBytes() {
        return backupSizeBytes;
    }
    
    public void setBackupSizeBytes(Long backupSizeBytes) {
        this.backupSizeBytes = backupSizeBytes;
    }
    
    public Double getCompressionRatio() {
        return compressionRatio;
    }
    
    public void setCompressionRatio(Double compressionRatio) {
        this.compressionRatio = compressionRatio;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public LocalDateTime getCompletedAt() {
        return completedAt;
    }
    
    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }
    
    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }
    
    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }
    
    public String getChecksum() {
        return checksum;
    }
    
    public void setChecksum(String checksum) {
        this.checksum = checksum;
    }
    
    public boolean isAutomatic() {
        return automatic;
    }
    
    public void setAutomatic(boolean automatic) {
        this.automatic = automatic;
    }
    
    public int getRestoreCount() {
        return restoreCount;
    }
    
    public void setRestoreCount(int restoreCount) {
        this.restoreCount = restoreCount;
    }
    
    public LocalDateTime getLastRestoreAt() {
        return lastRestoreAt;
    }
    
    public void setLastRestoreAt(LocalDateTime lastRestoreAt) {
        this.lastRestoreAt = lastRestoreAt;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
    
    public boolean isAutoBackupEnabled() {
        return autoBackupEnabled;
    }
    
    public void setAutoBackupEnabled(boolean autoBackupEnabled) {
        this.autoBackupEnabled = autoBackupEnabled;
    }
    
    public int getBackupIntervalHours() {
        return backupIntervalHours;
    }
    
    public void setBackupIntervalHours(int backupIntervalHours) {
        this.backupIntervalHours = backupIntervalHours;
    }
    
    public int getRetentionDays() {
        return retentionDays;
    }
    
    public void setRetentionDays(int retentionDays) {
        this.retentionDays = retentionDays;
    }
    
    public int getMaxBackups() {
        return maxBackups;
    }
    
    public void setMaxBackups(int maxBackups) {
        this.maxBackups = maxBackups;
    }
    
    /**
     * Estatísticas de backup do sistema
     */
    public static class BackupStats {
        private int totalBackups;
        private int completedBackups;
        private int failedBackups;
        private int automaticBackups;
        private int manualBackups;
        private long totalBackupSizeBytes;
        private double averageBackupSizeBytes;
        private int totalRestores;
        private int backupsLast24h;
        private int backupsLast7d;
        
        // Getters and Setters
        public int getTotalBackups() {
            return totalBackups;
        }
        
        public void setTotalBackups(int totalBackups) {
            this.totalBackups = totalBackups;
        }
        
        public int getCompletedBackups() {
            return completedBackups;
        }
        
        public void setCompletedBackups(int completedBackups) {
            this.completedBackups = completedBackups;
        }
        
        public int getFailedBackups() {
            return failedBackups;
        }
        
        public void setFailedBackups(int failedBackups) {
            this.failedBackups = failedBackups;
        }
        
        public int getAutomaticBackups() {
            return automaticBackups;
        }
        
        public void setAutomaticBackups(int automaticBackups) {
            this.automaticBackups = automaticBackups;
        }
        
        public int getManualBackups() {
            return manualBackups;
        }
        
        public void setManualBackups(int manualBackups) {
            this.manualBackups = manualBackups;
        }
        
        public long getTotalBackupSizeBytes() {
            return totalBackupSizeBytes;
        }
        
        public void setTotalBackupSizeBytes(long totalBackupSizeBytes) {
            this.totalBackupSizeBytes = totalBackupSizeBytes;
        }
        
        public double getAverageBackupSizeBytes() {
            return averageBackupSizeBytes;
        }
        
        public void setAverageBackupSizeBytes(double averageBackupSizeBytes) {
            this.averageBackupSizeBytes = averageBackupSizeBytes;
        }
        
        public int getTotalRestores() {
            return totalRestores;
        }
        
        public void setTotalRestores(int totalRestores) {
            this.totalRestores = totalRestores;
        }
        
        public int getBackupsLast24h() {
            return backupsLast24h;
        }
        
        public void setBackupsLast24h(int backupsLast24h) {
            this.backupsLast24h = backupsLast24h;
        }
        
        public int getBackupsLast7d() {
            return backupsLast7d;
        }
        
        public void setBackupsLast7d(int backupsLast7d) {
            this.backupsLast7d = backupsLast7d;
        }
    }
}
