package com.seveninterprise.clusterforge.controllers;

import com.seveninterprise.clusterforge.model.ClusterAlert;
import com.seveninterprise.clusterforge.model.ClusterBackup;
import com.seveninterprise.clusterforge.model.ClusterHealthStatus;
import com.seveninterprise.clusterforge.services.ClusterService;
import com.seveninterprise.clusterforge.services.IClusterBackupService;
import com.seveninterprise.clusterforge.services.IClusterHealthService;
import com.seveninterprise.clusterforge.services.IClusterMonitoringService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Controller REST para APIs de monitoramento e recuperação ante falha
 * 
 * Endpoints disponíveis:
 * - GET /api/health/clusters - Status de saúde de todos os clusters
 * - GET /api/health/clusters/{id} - Status de saúde de um cluster específico
 * - POST /api/health/clusters/{id}/recover - Forçar recuperação de um cluster
 * - GET /api/health/stats - Estatísticas gerais de saúde do sistema
 * 
 * - GET /api/backup/clusters/{id} - Listar backups de um cluster
 * - POST /api/backup/clusters/{id} - Criar backup de um cluster
 * - POST /api/backup/restore/{backupId} - Restaurar cluster a partir de backup
 * - GET /api/backup/stats - Estatísticas de backup do sistema
 * 
 * - GET /api/monitoring/dashboard - Dashboard de monitoramento
 * - GET /api/monitoring/alerts - Listar alertas ativos
 * - POST /api/monitoring/alerts/{id}/resolve - Resolver alerta
 */
@RestController
@RequestMapping("/api")
public class ClusterMonitoringController {
    
    @Autowired
    private ClusterService clusterService;
    
    @Autowired
    private IClusterHealthService healthService;
    
    @Autowired
    private IClusterBackupService backupService;
    
    @Autowired
    private IClusterMonitoringService monitoringService;
    
    // ============================================
    // ENDPOINTS DE HEALTH CHECK
    // ============================================
    
    /**
     * Obtém status de saúde de todos os clusters
     */
    @GetMapping("/health/clusters")
    public ResponseEntity<Map<Long, ClusterHealthStatus>> getAllClustersHealth() {
        try {
            Map<Long, ClusterHealthStatus> healthStatuses = healthService.checkAllClustersHealth();
            return ResponseEntity.ok(healthStatuses);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Obtém status de saúde de um cluster específico
     */
    @GetMapping("/health/clusters/{clusterId}")
    public ResponseEntity<ClusterHealthStatus> getClusterHealth(@PathVariable Long clusterId) {
        try {
            ClusterHealthStatus healthStatus = clusterService.getClusterHealthStatus(clusterId);
            return ResponseEntity.ok(healthStatus);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
    
    /**
     * Força recuperação de um cluster com falha
     */
    @PostMapping("/health/clusters/{clusterId}/recover")
    public ResponseEntity<Map<String, Object>> recoverCluster(@PathVariable Long clusterId) {
        try {
            boolean success = clusterService.recoverCluster(clusterId);
            
            Map<String, Object> response = Map.of(
                "success", success,
                "message", success ? "Cluster recuperado com sucesso" : "Falha na recuperação do cluster",
                "timestamp", LocalDateTime.now()
            );
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errorResponse = Map.of(
                "success", false,
                "message", "Erro durante recuperação: " + e.getMessage(),
                "timestamp", LocalDateTime.now()
            );
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
    
    /**
     * Obtém estatísticas gerais de saúde do sistema
     */
    @GetMapping("/health/stats")
    public ResponseEntity<ClusterHealthStatus.SystemHealthStats> getSystemHealthStats() {
        try {
            ClusterHealthStatus.SystemHealthStats stats = clusterService.getSystemHealthStats();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Força verificação de saúde de todos os clusters
     */
    @PostMapping("/health/force-check")
    public ResponseEntity<Map<String, Object>> forceHealthCheck() {
        try {
            healthService.forceHealthCheck();
            
            Map<String, Object> response = Map.of(
                "success", true,
                "message", "Verificação de saúde executada com sucesso",
                "timestamp", LocalDateTime.now()
            );
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errorResponse = Map.of(
                "success", false,
                "message", "Erro durante verificação: " + e.getMessage(),
                "timestamp", LocalDateTime.now()
            );
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
    
    // ============================================
    // ENDPOINTS DE BACKUP
    // ============================================
    
    /**
     * Lista backups de um cluster
     */
    @GetMapping("/backup/clusters/{clusterId}")
    public ResponseEntity<List<ClusterBackup>> listClusterBackups(@PathVariable Long clusterId) {
        try {
            List<ClusterBackup> backups = clusterService.listClusterBackups(clusterId);
            return ResponseEntity.ok(backups);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
    
    /**
     * Cria backup de um cluster
     */
    @PostMapping("/backup/clusters/{clusterId}")
    public ResponseEntity<ClusterBackup> createClusterBackup(
            @PathVariable Long clusterId,
            @RequestParam(defaultValue = "FULL") ClusterBackup.BackupType backupType,
            @RequestParam(defaultValue = "Backup manual") String description) {
        try {
            ClusterBackup backup = clusterService.createClusterBackup(clusterId, backupType, description);
            return ResponseEntity.ok(backup);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Restaura cluster a partir de backup
     */
    @PostMapping("/backup/restore/{backupId}")
    public ResponseEntity<Map<String, Object>> restoreFromBackup(
            @PathVariable Long backupId,
            @RequestParam(required = false) Long clusterId) {
        try {
            boolean success = clusterService.restoreClusterFromBackup(backupId, clusterId);
            
            Map<String, Object> response = Map.of(
                "success", success,
                "message", success ? "Cluster restaurado com sucesso" : "Falha na restauração do cluster",
                "timestamp", LocalDateTime.now()
            );
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errorResponse = Map.of(
                "success", false,
                "message", "Erro durante restauração: " + e.getMessage(),
                "timestamp", LocalDateTime.now()
            );
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
    
    /**
     * Obtém estatísticas de backup do sistema
     */
    @GetMapping("/backup/stats")
    public ResponseEntity<ClusterBackup.BackupStats> getBackupStats() {
        try {
            ClusterBackup.BackupStats stats = clusterService.getSystemBackupStats();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Lista todos os backups do sistema
     */
    @GetMapping("/backup/all")
    public ResponseEntity<List<ClusterBackup>> listAllBackups() {
        try {
            List<ClusterBackup> backups = backupService.listAllBackups();
            return ResponseEntity.ok(backups);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Remove backup antigo
     */
    @DeleteMapping("/backup/{backupId}")
    public ResponseEntity<Map<String, Object>> deleteBackup(@PathVariable Long backupId) {
        try {
            boolean success = backupService.deleteBackup(backupId);
            
            Map<String, Object> response = Map.of(
                "success", success,
                "message", success ? "Backup removido com sucesso" : "Falha ao remover backup",
                "timestamp", LocalDateTime.now()
            );
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errorResponse = Map.of(
                "success", false,
                "message", "Erro ao remover backup: " + e.getMessage(),
                "timestamp", LocalDateTime.now()
            );
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
    
    // ============================================
    // ENDPOINTS DE MONITORAMENTO
    // ============================================
    
    /**
     * Obtém dashboard de monitoramento
     */
    @GetMapping("/monitoring/dashboard")
    public ResponseEntity<IClusterMonitoringService.MonitoringDashboard> getMonitoringDashboard() {
        try {
            IClusterMonitoringService.MonitoringDashboard dashboard = monitoringService.getMonitoringDashboard();
            return ResponseEntity.ok(dashboard);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Lista alertas ativos
     */
    @GetMapping("/monitoring/alerts")
    public ResponseEntity<List<ClusterAlert>> getActiveAlerts(
            @RequestParam(required = false) ClusterAlert.AlertSeverity severity) {
        try {
            List<ClusterAlert> alerts = monitoringService.listAllAlerts(severity, false);
            return ResponseEntity.ok(alerts);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Lista alertas de um cluster específico
     */
    @GetMapping("/monitoring/clusters/{clusterId}/alerts")
    public ResponseEntity<List<ClusterAlert>> getClusterAlerts(
            @PathVariable Long clusterId,
            @RequestParam(defaultValue = "false") boolean includeResolved) {
        try {
            List<ClusterAlert> alerts = monitoringService.listClusterAlerts(clusterId, includeResolved);
            return ResponseEntity.ok(alerts);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
    
    /**
     * Resolve um alerta
     */
    @PostMapping("/monitoring/alerts/{alertId}/resolve")
    public ResponseEntity<Map<String, Object>> resolveAlert(
            @PathVariable Long alertId,
            @RequestParam(defaultValue = "Resolvido manualmente") String resolutionMessage) {
        try {
            monitoringService.resolveAlert(alertId, resolutionMessage);
            
            Map<String, Object> response = Map.of(
                "success", true,
                "message", "Alerta resolvido com sucesso",
                "timestamp", LocalDateTime.now()
            );
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errorResponse = Map.of(
                "success", false,
                "message", "Erro ao resolver alerta: " + e.getMessage(),
                "timestamp", LocalDateTime.now()
            );
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
    
    /**
     * Obtém métricas em tempo real de um cluster
     */
    @GetMapping("/monitoring/clusters/{clusterId}/metrics")
    public ResponseEntity<Map<String, Object>> getClusterMetrics(@PathVariable Long clusterId) {
        try {
            Map<String, Object> metrics = monitoringService.getRealtimeMetrics(clusterId);
            return ResponseEntity.ok(metrics);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
    
    /**
     * Obtém métricas históricas de um cluster
     */
    @GetMapping("/monitoring/clusters/{clusterId}/metrics/history")
    public ResponseEntity<List<Map<String, Object>>> getClusterMetricsHistory(
            @PathVariable Long clusterId,
            @RequestParam LocalDateTime startTime,
            @RequestParam LocalDateTime endTime) {
        try {
            List<Map<String, Object>> metrics = monitoringService.getHistoricalMetrics(clusterId, startTime, endTime);
            return ResponseEntity.ok(metrics);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
    
    /**
     * Obtém estatísticas de monitoramento
     */
    @GetMapping("/monitoring/stats")
    public ResponseEntity<IClusterMonitoringService.MonitoringStats> getMonitoringStats() {
        try {
            IClusterMonitoringService.MonitoringStats stats = monitoringService.getMonitoringStats();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
    
    // ============================================
    // ENDPOINTS DE CONFIGURAÇÃO
    // ============================================
    
    /**
     * Configura política de recuperação para um cluster
     */
    @PostMapping("/health/clusters/{clusterId}/policy")
    public ResponseEntity<Map<String, Object>> configureRecoveryPolicy(
            @PathVariable Long clusterId,
            @RequestParam int maxRetries,
            @RequestParam int retryInterval,
            @RequestParam int cooldownPeriod) {
        try {
            healthService.configureRecoveryPolicy(clusterId, maxRetries, retryInterval, cooldownPeriod);
            
            Map<String, Object> response = Map.of(
                "success", true,
                "message", "Política de recuperação configurada com sucesso",
                "timestamp", LocalDateTime.now()
            );
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errorResponse = Map.of(
                "success", false,
                "message", "Erro ao configurar política: " + e.getMessage(),
                "timestamp", LocalDateTime.now()
            );
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
    
    /**
     * Configura política de backup para um cluster
     */
    @PostMapping("/backup/clusters/{clusterId}/policy")
    public ResponseEntity<Map<String, Object>> configureBackupPolicy(
            @PathVariable Long clusterId,
            @RequestParam boolean autoBackupEnabled,
            @RequestParam int backupIntervalHours,
            @RequestParam int retentionDays,
            @RequestParam int maxBackups) {
        try {
            backupService.configureBackupPolicy(clusterId, autoBackupEnabled, backupIntervalHours, retentionDays, maxBackups);
            
            Map<String, Object> response = Map.of(
                "success", true,
                "message", "Política de backup configurada com sucesso",
                "timestamp", LocalDateTime.now()
            );
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errorResponse = Map.of(
                "success", false,
                "message", "Erro ao configurar política de backup: " + e.getMessage(),
                "timestamp", LocalDateTime.now()
            );
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
}
