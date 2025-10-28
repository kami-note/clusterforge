package com.seveninterprise.clusterforge.services;

import com.seveninterprise.clusterforge.model.Cluster;
import com.seveninterprise.clusterforge.model.ClusterBackup;
import com.seveninterprise.clusterforge.repository.ClusterRepository;
import com.seveninterprise.clusterforge.repositories.ClusterBackupRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;

import jakarta.annotation.PostConstruct;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.GZIPOutputStream;

/**
 * Serviço de backup e recuperação de clusters
 * 
 * Funcionalidades:
 * - Backup automático e manual
 * - Compressão e otimização
 * - Verificação de integridade
 * - Políticas de retenção
 * - Restauração pontual
 */
@Service
public class ClusterBackupService implements IClusterBackupService {
    
    private final ClusterRepository clusterRepository;
    private final ClusterBackupRepository backupRepository;
    private final DockerService dockerService;
    private ExecutorService executorService;
    
    @Value("${clusterforge.backup.directory:/home/levi/Projects/clusterforge-f/backend/data/backups}")
    private String backupBaseDirectory;
    
    @Value("${clusterforge.backup.max.concurrent:3}")
    private int maxConcurrentBackups;
    
    @Value("${clusterforge.backup.compression.enabled:true}")
    private boolean compressionEnabled;
    
    public ClusterBackupService(ClusterRepository clusterRepository,
                              ClusterBackupRepository backupRepository,
                              DockerService dockerService) {
        this.clusterRepository = clusterRepository;
        this.backupRepository = backupRepository;
        this.dockerService = dockerService;
        // Inicializar executorService e diretório no @PostConstruct
    }
    
    @PostConstruct
    public void init() {
        this.executorService = Executors.newFixedThreadPool(Math.max(1, maxConcurrentBackups));
        
        // Criar diretório de backups se não existir
        try {
            Files.createDirectories(Paths.get(backupBaseDirectory));
        } catch (IOException e) {
            System.err.println("Erro ao criar diretório de backups: " + e.getMessage());
        }
    }
    
    @Override
    @Transactional
    public ClusterBackup createBackup(Long clusterId, ClusterBackup.BackupType backupType, String description) {
        Cluster cluster = clusterRepository.findById(clusterId)
            .orElseThrow(() -> new RuntimeException("Cluster não encontrado: " + clusterId));
        
        // Criar registro de backup
        ClusterBackup backup = new ClusterBackup();
        backup.setCluster(cluster);
        backup.setBackupType(backupType);
        backup.setDescription(description);
        backup.setAutomatic(false);
        backup.setStatus(ClusterBackup.BackupStatus.IN_PROGRESS);
        
        // Definir data de expiração baseada na política de retenção
        backup.setExpiresAt(LocalDateTime.now().plusDays(backup.getRetentionDays()));
        
        backup = backupRepository.save(backup);
        
        // Executar backup em thread separada
        final ClusterBackup finalBackup = backup;
        final Long finalClusterId = clusterId;
        CompletableFuture.runAsync(() -> {
            try {
                performBackup(finalBackup);
            } catch (Exception e) {
                finalBackup.setStatus(ClusterBackup.BackupStatus.FAILED);
                finalBackup.setErrorMessage(e.getMessage());
                backupRepository.save(finalBackup);
                System.err.println("Erro durante backup do cluster " + finalClusterId + ": " + e.getMessage());
            }
        }, executorService);
        
        return backup;
    }
    
    @Override
    @Transactional
    public int createAutomaticBackups() {
        List<Cluster> activeClusters = clusterRepository.findAll();
        int backupCount = 0;
        
        for (Cluster cluster : activeClusters) {
            if (shouldCreateAutomaticBackup(cluster)) {
                try {
                    ClusterBackup backup = new ClusterBackup();
                    backup.setCluster(cluster);
                    backup.setBackupType(ClusterBackup.BackupType.INCREMENTAL);
                    backup.setDescription("Backup automático");
                    backup.setAutomatic(true);
                    backup.setStatus(ClusterBackup.BackupStatus.IN_PROGRESS);
                    backup.setExpiresAt(LocalDateTime.now().plusDays(backup.getRetentionDays()));
                    
                    backup = backupRepository.save(backup);
                    
                    // Executar backup
                    performBackup(backup);
                    backupCount++;
                    
                } catch (Exception e) {
                    System.err.println("Erro no backup automático do cluster " + cluster.getId() + ": " + e.getMessage());
                }
            }
        }
        
        System.out.println("Backups automáticos criados: " + backupCount);
        return backupCount;
    }
    
    @Override
    @Transactional
    public boolean restoreFromBackup(Long backupId, Long clusterId) {
        ClusterBackup backup = backupRepository.findById(backupId)
            .orElseThrow(() -> new RuntimeException("Backup não encontrado: " + backupId));
        
        Cluster targetCluster = clusterId != null ? 
            clusterRepository.findById(clusterId).orElse(backup.getCluster()) : 
            backup.getCluster();
        
        try {
            // Verificar integridade do backup
            if (!verifyBackupIntegrity(backupId)) {
                throw new RuntimeException("Backup corrompido ou inválido");
            }
            
            // Parar container se estiver rodando
            String containerName = targetCluster.getSanitizedContainerName();
            try {
                dockerService.stopContainer(containerName);
                Thread.sleep(3000);
            } catch (Exception e) {
                // Ignora se não conseguir parar
            }
            
            // Restaurar dados
            boolean restoreSuccess = performRestore(backup, targetCluster);
            
            if (restoreSuccess) {
                // Atualizar estatísticas do backup
                backup.setRestoreCount(backup.getRestoreCount() + 1);
                backup.setLastRestoreAt(LocalDateTime.now());
                backupRepository.save(backup);
                
                System.out.println("Cluster " + targetCluster.getId() + " restaurado do backup " + backupId);
                return true;
            } else {
                throw new RuntimeException("Falha na restauração");
            }
            
        } catch (Exception e) {
            System.err.println("Erro durante restauração do backup " + backupId + ": " + e.getMessage());
            return false;
        }
    }
    
    @Override
    public List<ClusterBackup> listClusterBackups(Long clusterId) {
        return backupRepository.findByClusterIdOrderByCreatedAtDesc(clusterId);
    }
    
    @Override
    public List<ClusterBackup> listAllBackups() {
        return backupRepository.findAllByOrderByCreatedAtDesc();
    }
    
    @Override
    public Optional<ClusterBackup> getBackupById(Long backupId) {
        return backupRepository.findById(backupId);
    }
    
    @Override
    public boolean verifyBackupIntegrity(Long backupId) {
        ClusterBackup backup = backupRepository.findById(backupId)
            .orElseThrow(() -> new RuntimeException("Backup não encontrado: " + backupId));
        
        try {
            Path backupPath = Paths.get(backup.getBackupPath());
            if (!Files.exists(backupPath)) {
                return false;
            }
            
            // Verificar checksum se disponível
            if (backup.getChecksum() != null) {
                String currentChecksum = calculateChecksum(backupPath);
                if (!backup.getChecksum().equals(currentChecksum)) {
                    return false;
                }
            }
            
            // Verificar se arquivo não está corrompido
            return isBackupFileValid(backupPath);
            
        } catch (Exception e) {
            System.err.println("Erro ao verificar integridade do backup " + backupId + ": " + e.getMessage());
            return false;
        }
    }
    
    @Override
    @Transactional
    public boolean deleteBackup(Long backupId) {
        ClusterBackup backup = backupRepository.findById(backupId)
            .orElseThrow(() -> new RuntimeException("Backup não encontrado: " + backupId));
        
        try {
            // Remover arquivo físico
            Path backupPath = Paths.get(backup.getBackupPath());
            if (Files.exists(backupPath)) {
                Files.delete(backupPath);
            }
            
            // Remover registro do banco
            backupRepository.delete(backup);
            
            System.out.println("Backup " + backupId + " removido com sucesso");
            return true;
            
        } catch (Exception e) {
            System.err.println("Erro ao remover backup " + backupId + ": " + e.getMessage());
            return false;
        }
    }
    
    @Override
    @Transactional
    public int cleanupOldBackups() {
        LocalDateTime cutoffTime = LocalDateTime.now().minusDays(30); // Padrão: 30 dias
        
        List<ClusterBackup> oldBackups = backupRepository.findByExpiresAtBeforeOrStatus(cutoffTime, ClusterBackup.BackupStatus.CORRUPTED);
        
        int removedCount = 0;
        for (ClusterBackup backup : oldBackups) {
            if (deleteBackup(backup.getId())) {
                removedCount++;
            }
        }
        
        System.out.println("Backups antigos removidos: " + removedCount);
        return removedCount;
    }
    
    @Override
    @Transactional
    public void configureBackupPolicy(Long clusterId, boolean autoBackupEnabled, 
                                     int backupIntervalHours, int retentionDays, int maxBackups) {
        List<ClusterBackup> clusterBackups = backupRepository.findByClusterIdOrderByCreatedAtDesc(clusterId);
        
        // Aplicar configurações aos backups existentes
        for (ClusterBackup backup : clusterBackups) {
            backup.setAutoBackupEnabled(autoBackupEnabled);
            backup.setBackupIntervalHours(backupIntervalHours);
            backup.setRetentionDays(retentionDays);
            backup.setMaxBackups(maxBackups);
            backupRepository.save(backup);
        }
    }
    
    @Override
    public ClusterBackup.BackupStats getBackupStats() {
        List<ClusterBackup> allBackups = backupRepository.findAll();
        
        ClusterBackup.BackupStats stats = new ClusterBackup.BackupStats();
        
        stats.setTotalBackups(allBackups.size());
        stats.setCompletedBackups((int) allBackups.stream()
            .filter(b -> b.getStatus() == ClusterBackup.BackupStatus.COMPLETED)
            .count());
        stats.setFailedBackups((int) allBackups.stream()
            .filter(b -> b.getStatus() == ClusterBackup.BackupStatus.FAILED)
            .count());
        stats.setAutomaticBackups((int) allBackups.stream()
            .filter(ClusterBackup::isAutomatic)
            .count());
        stats.setManualBackups((int) allBackups.stream()
            .filter(b -> !b.isAutomatic())
            .count());
        
        // Calcular tamanhos
        long totalSize = allBackups.stream()
            .filter(b -> b.getBackupSizeBytes() != null)
            .mapToLong(ClusterBackup::getBackupSizeBytes)
            .sum();
        stats.setTotalBackupSizeBytes(totalSize);
        
        double avgSize = allBackups.stream()
            .filter(b -> b.getBackupSizeBytes() != null)
            .mapToLong(ClusterBackup::getBackupSizeBytes)
            .average()
            .orElse(0.0);
        stats.setAverageBackupSizeBytes(avgSize);
        
        // Contar restaurações
        stats.setTotalRestores(allBackups.stream()
            .mapToInt(ClusterBackup::getRestoreCount)
            .sum());
        
        // Contar backups recentes
        LocalDateTime last24h = LocalDateTime.now().minus(24, ChronoUnit.HOURS);
        LocalDateTime last7d = LocalDateTime.now().minus(7, ChronoUnit.DAYS);
        
        stats.setBackupsLast24h((int) allBackups.stream()
            .filter(b -> b.getCreatedAt().isAfter(last24h))
            .count());
        
        stats.setBackupsLast7d((int) allBackups.stream()
            .filter(b -> b.getCreatedAt().isAfter(last7d))
            .count());
        
        return stats;
    }
    
    @Override
    public boolean exportBackup(Long backupId, String exportPath) {
        ClusterBackup backup = backupRepository.findById(backupId)
            .orElseThrow(() -> new RuntimeException("Backup não encontrado: " + backupId));
        
        try {
            Path sourcePath = Paths.get(backup.getBackupPath());
            Path targetPath = Paths.get(exportPath);
            
            Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
            
            System.out.println("Backup " + backupId + " exportado para " + exportPath);
            return true;
            
        } catch (Exception e) {
            System.err.println("Erro ao exportar backup " + backupId + ": " + e.getMessage());
            return false;
        }
    }
    
    @Override
    @Transactional
    public ClusterBackup importBackup(String importPath, Long clusterId) {
        Cluster cluster = clusterRepository.findById(clusterId)
            .orElseThrow(() -> new RuntimeException("Cluster não encontrado: " + clusterId));
        
        try {
            Path sourcePath = Paths.get(importPath);
            String backupFileName = "imported_" + System.currentTimeMillis() + ".tar.gz";
            Path targetPath = Paths.get(backupBaseDirectory, backupFileName);
            
            Files.copy(sourcePath, targetPath);
            
            ClusterBackup backup = new ClusterBackup();
            backup.setCluster(cluster);
            backup.setBackupType(ClusterBackup.BackupType.FULL);
            backup.setDescription("Backup importado de " + importPath);
            backup.setBackupPath(targetPath.toString());
            backup.setStatus(ClusterBackup.BackupStatus.COMPLETED);
            backup.setCompletedAt(LocalDateTime.now());
            backup.setAutomatic(false);
            
            // Calcular tamanho e checksum
            try {
                backup.setBackupSizeBytes(Files.size(targetPath));
            } catch (IOException e) {
                backup.setBackupSizeBytes(0L);
            }
            backup.setChecksum(calculateChecksum(targetPath));
            
            backup = backupRepository.save(backup);
            
            System.out.println("Backup importado com sucesso: " + backup.getId());
            return backup;
            
        } catch (Exception e) {
            System.err.println("Erro ao importar backup: " + e.getMessage());
            throw new RuntimeException("Falha na importação do backup", e);
        }
    }
    
    // Métodos auxiliares privados
    
    private boolean shouldCreateAutomaticBackup(Cluster cluster) {
        List<ClusterBackup> recentBackups = backupRepository.findByClusterIdOrderByCreatedAtDesc(cluster.getId());
        
        if (recentBackups.isEmpty()) {
            return true; // Primeiro backup
        }
        
        ClusterBackup lastBackup = recentBackups.get(0);
        
        // Verificar se último backup foi há mais de X horas
        LocalDateTime lastBackupTime = lastBackup.getCreatedAt();
        LocalDateTime nextBackupTime = lastBackupTime.plusHours(lastBackup.getBackupIntervalHours());
        
        return LocalDateTime.now().isAfter(nextBackupTime);
    }
    
    private void performBackup(ClusterBackup backup) {
        try {
            Cluster cluster = backup.getCluster();
            String clusterPath = cluster.getRootPath();
            
            // Criar nome do arquivo de backup
            String timestamp = LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String backupFileName = String.format("cluster_%d_%s_%s.tar%s", 
                cluster.getId(), timestamp, backup.getBackupType().name().toLowerCase(),
                compressionEnabled ? ".gz" : "");
            
            Path backupPath = Paths.get(backupBaseDirectory, backupFileName);
            backup.setBackupPath(backupPath.toString());
            
            // Executar backup baseado no tipo
            boolean success = false;
            switch (backup.getBackupType()) {
                case FULL:
                    success = createFullBackup(clusterPath, backupPath);
                    break;
                case INCREMENTAL:
                    success = createIncrementalBackup(clusterPath, backupPath, cluster.getId());
                    break;
                case CONFIG_ONLY:
                    success = createConfigOnlyBackup(clusterPath, backupPath);
                    break;
                case DATA_ONLY:
                    success = createDataOnlyBackup(clusterPath, backupPath);
                    break;
            }
            
            if (success) {
                backup.setStatus(ClusterBackup.BackupStatus.COMPLETED);
                backup.setCompletedAt(LocalDateTime.now());
                try {
                    backup.setBackupSizeBytes(Files.size(backupPath));
                } catch (IOException e) {
                    backup.setBackupSizeBytes(0L);
                }
                backup.setChecksum(calculateChecksum(backupPath));
                
                if (compressionEnabled) {
                    backup.setCompressionRatio(calculateCompressionRatio(backupPath));
                }
            } else {
                backup.setStatus(ClusterBackup.BackupStatus.FAILED);
                backup.setErrorMessage("Falha durante criação do backup");
            }
            
            backupRepository.save(backup);
            
        } catch (Exception e) {
            backup.setStatus(ClusterBackup.BackupStatus.FAILED);
            backup.setErrorMessage(e.getMessage());
            backupRepository.save(backup);
            throw e;
        }
    }
    
    private boolean createFullBackup(String clusterPath, Path backupPath) {
        try {
            String command = String.format("tar -czf %s -C %s .", backupPath.toString(), clusterPath);
            String result = dockerService.runCommand(command);
            return result.contains("Process exited with code: 0");
        } catch (Exception e) {
            System.err.println("Erro no backup completo: " + e.getMessage());
            return false;
        }
    }
    
    private boolean createIncrementalBackup(String clusterPath, Path backupPath, Long clusterId) {
        try {
            // Buscar último backup para comparação
            List<ClusterBackup> recentBackups = backupRepository.findByClusterIdOrderByCreatedAtDesc(clusterId);
            if (recentBackups.isEmpty()) {
                return createFullBackup(clusterPath, backupPath);
            }
            
            // Implementar backup incremental usando rsync ou similar
            String command = String.format("tar -czf %s -C %s .", backupPath.toString(), clusterPath);
            String result = dockerService.runCommand(command);
            return result.contains("Process exited with code: 0");
        } catch (Exception e) {
            System.err.println("Erro no backup incremental: " + e.getMessage());
            return false;
        }
    }
    
    private boolean createConfigOnlyBackup(String clusterPath, Path backupPath) {
        try {
            String command = String.format("tar -czf %s -C %s docker-compose.yml", backupPath.toString(), clusterPath);
            String result = dockerService.runCommand(command);
            return result.contains("Process exited with code: 0");
        } catch (Exception e) {
            System.err.println("Erro no backup de configuração: " + e.getMessage());
            return false;
        }
    }
    
    private boolean createDataOnlyBackup(String clusterPath, Path backupPath) {
        try {
            String command = String.format("tar -czf %s -C %s src/", backupPath.toString(), clusterPath);
            String result = dockerService.runCommand(command);
            return result.contains("Process exited with code: 0");
        } catch (Exception e) {
            System.err.println("Erro no backup de dados: " + e.getMessage());
            return false;
        }
    }
    
    private boolean performRestore(ClusterBackup backup, Cluster targetCluster) {
        try {
            String clusterPath = targetCluster.getRootPath();
            Path backupPath = Paths.get(backup.getBackupPath());
            
            // Parar container se estiver rodando
            String containerName = targetCluster.getSanitizedContainerName();
            try {
                dockerService.stopContainer(containerName);
                Thread.sleep(2000);
            } catch (Exception e) {
                // Ignora se não conseguir parar
            }
            
            // Extrair backup
            String command = String.format("tar -xzf %s -C %s", backupPath.toString(), clusterPath);
            String result = dockerService.runCommand(command);
            
            if (result.contains("Process exited with code: 0")) {
                // Reiniciar container
                dockerService.startContainer(containerName);
                return true;
            } else {
                return false;
            }
            
        } catch (Exception e) {
            System.err.println("Erro durante restauração: " + e.getMessage());
            return false;
        }
    }
    
    private String calculateChecksum(Path filePath) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] data = Files.readAllBytes(filePath);
            byte[] hash = md.digest(data);
            
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
            
        } catch (Exception e) {
            System.err.println("Erro ao calcular checksum: " + e.getMessage());
            return null;
        }
    }
    
    private boolean isBackupFileValid(Path backupPath) {
        try {
            // Verificar se arquivo não está vazio
            if (Files.size(backupPath) == 0) {
                return false;
            }
            
            // Tentar ler cabeçalho do arquivo tar
            try (InputStream is = Files.newInputStream(backupPath)) {
                byte[] header = new byte[512];
                int bytesRead = is.read(header);
                return bytesRead == 512; // Cabeçalho tar tem 512 bytes
            }
            
        } catch (Exception e) {
            return false;
        }
    }
    
    private Double calculateCompressionRatio(Path backupPath) {
        try {
            // Implementar cálculo de taxa de compressão
            // Por enquanto, retorna valor padrão
            return 0.7; // 70% de compressão
        } catch (Exception e) {
            return null;
        }
    }
    
    // Agendamento automático de backups
    @Scheduled(fixedDelayString = "${clusterforge.backup.automatic.interval:3600000}")
    public void scheduledAutomaticBackups() {
        System.out.println("Executando backups automáticos...");
        createAutomaticBackups();
    }
    
    // Agendamento de limpeza de backups antigos
    @Scheduled(fixedDelayString = "${clusterforge.backup.cleanup.interval:86400000}")
    public void scheduledBackupCleanup() {
        System.out.println("Executando limpeza de backups antigos...");
        cleanupOldBackups();
    }
}
