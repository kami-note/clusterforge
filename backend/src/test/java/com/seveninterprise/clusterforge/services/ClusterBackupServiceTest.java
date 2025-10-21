package com.seveninterprise.clusterforge.services;

import com.seveninterprise.clusterforge.model.Cluster;
import com.seveninterprise.clusterforge.model.ClusterBackup;
import com.seveninterprise.clusterforge.repositories.ClusterRepository;
import com.seveninterprise.clusterforge.repositories.ClusterBackupRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Testes unitários para ClusterBackupService
 * 
 * Testa funcionalidades de:
 * - Criação de backups
 * - Restauração de backups
 * - Verificação de integridade
 * - Políticas de retenção
 * - Limpeza de backups antigos
 */
@ExtendWith(MockitoExtension.class)
class ClusterBackupServiceTest {
    
    @Mock
    private ClusterRepository clusterRepository;
    
    @Mock
    private ClusterBackupRepository backupRepository;
    
    @Mock
    private DockerService dockerService;
    
    private ClusterBackupService backupService;
    
    private Cluster testCluster;
    private ClusterBackup testBackup;
    
    @BeforeEach
    void setUp() {
        backupService = new ClusterBackupService(
            clusterRepository,
            backupRepository,
            dockerService
        );
        
        // Setup test cluster
        testCluster = new Cluster();
        testCluster.setId(1L);
        testCluster.setName("test-cluster");
        testCluster.setPort(8080);
        testCluster.setRootPath("/test/path");
        
        // Setup test backup
        testBackup = new ClusterBackup();
        testBackup.setId(1L);
        testBackup.setCluster(testCluster);
        testBackup.setBackupType(ClusterBackup.BackupType.FULL);
        testBackup.setStatus(ClusterBackup.BackupStatus.COMPLETED);
        testBackup.setBackupPath("/backup/test-backup.tar.gz");
        testBackup.setBackupSizeBytes(1024000L);
        testBackup.setChecksum("abc123def456");
        testBackup.setCreatedAt(LocalDateTime.now());
        testBackup.setCompletedAt(LocalDateTime.now());
    }
    
    @Test
    void testCreateBackup_FullBackup() {
        // Arrange
        when(clusterRepository.findById(1L)).thenReturn(Optional.of(testCluster));
        when(backupRepository.save(any(ClusterBackup.class))).thenReturn(testBackup);
        when(dockerService.runCommand(anyString())).thenReturn("Process exited with code: 0");
        
        // Act
        ClusterBackup result = backupService.createBackup(1L, ClusterBackup.BackupType.FULL, "Test backup");
        
        // Assert
        assertNotNull(result);
        assertEquals(ClusterBackup.BackupType.FULL, result.getBackupType());
        assertEquals("Test backup", result.getDescription());
        assertFalse(result.isAutomatic());
        verify(backupRepository).save(any(ClusterBackup.class));
    }
    
    @Test
    void testCreateBackup_ClusterNotFound() {
        // Arrange
        when(clusterRepository.findById(1L)).thenReturn(Optional.empty());
        
        // Act & Assert
        assertThrows(RuntimeException.class, () -> {
            backupService.createBackup(1L, ClusterBackup.BackupType.FULL, "Test backup");
        });
    }
    
    @Test
    void testCreateAutomaticBackups() {
        // Arrange
        List<Cluster> clusters = Arrays.asList(testCluster);
        when(clusterRepository.findAll()).thenReturn(clusters);
        when(backupRepository.save(any(ClusterBackup.class))).thenReturn(testBackup);
        when(dockerService.runCommand(anyString())).thenReturn("Process exited with code: 0");
        
        // Mock shouldCreateAutomaticBackup to return true
        when(backupRepository.findByClusterIdOrderByCreatedAtDesc(1L)).thenReturn(new ArrayList<>());
        
        // Act
        int backupCount = backupService.createAutomaticBackups();
        
        // Assert
        assertEquals(1, backupCount);
        verify(backupRepository, atLeastOnce()).save(any(ClusterBackup.class));
    }
    
    @Test
    void testRestoreFromBackup_SuccessfulRestore() {
        // Arrange
        when(backupRepository.findById(1L)).thenReturn(Optional.of(testBackup));
        when(dockerService.runCommand(contains("stop"))).thenReturn("Process exited with code: 0");
        when(dockerService.runCommand(contains("rm"))).thenReturn("Process exited with code: 0");
        when(dockerService.runCommand(contains("start"))).thenReturn("Process exited with code: 0");
        when(dockerService.runCommand(contains("tar -xzf"))).thenReturn("Process exited with code: 0");
        
        // Act
        boolean result = backupService.restoreFromBackup(1L, null);
        
        // Assert
        assertTrue(result);
        verify(dockerService).runCommand(contains("stop"));
        verify(dockerService).runCommand(contains("tar -xzf"));
        verify(backupRepository).save(any(ClusterBackup.class));
    }
    
    @Test
    void testRestoreFromBackup_BackupNotFound() {
        // Arrange
        when(backupRepository.findById(1L)).thenReturn(Optional.empty());
        
        // Act & Assert
        assertThrows(RuntimeException.class, () -> {
            backupService.restoreFromBackup(1L, null);
        });
    }
    
    @Test
    void testRestoreFromBackup_CorruptedBackup() {
        // Arrange
        when(backupRepository.findById(1L)).thenReturn(Optional.of(testBackup));
        when(dockerService.runCommand(anyString())).thenReturn("Process exited with code: 1");
        
        // Act
        boolean result = backupService.restoreFromBackup(1L, null);
        
        // Assert
        assertFalse(result);
    }
    
    @Test
    void testListClusterBackups() {
        // Arrange
        List<ClusterBackup> backups = Arrays.asList(testBackup);
        when(backupRepository.findByClusterIdOrderByCreatedAtDesc(1L)).thenReturn(backups);
        
        // Act
        List<ClusterBackup> result = backupService.listClusterBackups(1L);
        
        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(testBackup.getId(), result.get(0).getId());
    }
    
    @Test
    void testGetBackupById() {
        // Arrange
        when(backupRepository.findById(1L)).thenReturn(Optional.of(testBackup));
        
        // Act
        Optional<ClusterBackup> result = backupService.getBackupById(1L);
        
        // Assert
        assertTrue(result.isPresent());
        assertEquals(testBackup.getId(), result.get().getId());
    }
    
    @Test
    void testVerifyBackupIntegrity_ValidBackup() {
        // Arrange
        when(backupRepository.findById(1L)).thenReturn(Optional.of(testBackup));
        when(dockerService.runCommand(anyString())).thenReturn("Process exited with code: 0");
        
        // Act
        boolean result = backupService.verifyBackupIntegrity(1L);
        
        // Assert
        assertTrue(result);
    }
    
    @Test
    void testVerifyBackupIntegrity_InvalidChecksum() {
        // Arrange
        ClusterBackup corruptedBackup = new ClusterBackup();
        corruptedBackup.setId(1L);
        corruptedBackup.setCluster(testCluster);
        corruptedBackup.setBackupPath("/backup/corrupted-backup.tar.gz");
        corruptedBackup.setChecksum("wrong-checksum");
        
        when(backupRepository.findById(1L)).thenReturn(Optional.of(corruptedBackup));
        when(dockerService.runCommand(anyString())).thenReturn("Process exited with code: 0");
        
        // Act
        boolean result = backupService.verifyBackupIntegrity(1L);
        
        // Assert
        assertFalse(result);
    }
    
    @Test
    void testDeleteBackup() {
        // Arrange
        when(backupRepository.findById(1L)).thenReturn(Optional.of(testBackup));
        when(dockerService.runCommand(anyString())).thenReturn("Process exited with code: 0");
        
        // Act
        boolean result = backupService.deleteBackup(1L);
        
        // Assert
        assertTrue(result);
        verify(backupRepository).delete(testBackup);
    }
    
    @Test
    void testCleanupOldBackups() {
        // Arrange
        LocalDateTime cutoffTime = LocalDateTime.now().minusDays(30);
        ClusterBackup oldBackup = new ClusterBackup();
        oldBackup.setId(2L);
        oldBackup.setExpiresAt(cutoffTime.minusDays(1));
        oldBackup.setStatus(ClusterBackup.BackupStatus.COMPLETED);
        
        List<ClusterBackup> oldBackups = Arrays.asList(oldBackup);
        when(backupRepository.findByExpiresAtBeforeOrStatus(cutoffTime, ClusterBackup.BackupStatus.CORRUPTED))
            .thenReturn(oldBackups);
        when(dockerService.runCommand(anyString())).thenReturn("Process exited with code: 0");
        
        // Act
        int removedCount = backupService.cleanupOldBackups();
        
        // Assert
        assertEquals(1, removedCount);
        verify(backupRepository).delete(oldBackup);
    }
    
    @Test
    void testConfigureBackupPolicy() {
        // Arrange
        List<ClusterBackup> clusterBackups = Arrays.asList(testBackup);
        when(backupRepository.findByClusterIdOrderByCreatedAtDesc(1L)).thenReturn(clusterBackups);
        
        // Act
        backupService.configureBackupPolicy(1L, true, 12, 7, 5);
        
        // Assert
        assertEquals(12, testBackup.getBackupIntervalHours());
        assertEquals(7, testBackup.getRetentionDays());
        assertEquals(5, testBackup.getMaxBackups());
        assertTrue(testBackup.isAutoBackupEnabled());
        verify(backupRepository).save(testBackup);
    }
    
    @Test
    void testGetBackupStats() {
        // Arrange
        ClusterBackup completedBackup = new ClusterBackup();
        completedBackup.setStatus(ClusterBackup.BackupStatus.COMPLETED);
        completedBackup.setBackupSizeBytes(1024000L);
        completedBackup.setAutomatic(true);
        completedBackup.setRestoreCount(2);
        completedBackup.setCreatedAt(LocalDateTime.now().minusHours(1));
        
        ClusterBackup failedBackup = new ClusterBackup();
        failedBackup.setStatus(ClusterBackup.BackupStatus.FAILED);
        failedBackup.setAutomatic(false);
        
        List<ClusterBackup> allBackups = Arrays.asList(completedBackup, failedBackup);
        when(backupRepository.findAll()).thenReturn(allBackups);
        
        // Act
        ClusterBackup.BackupStats stats = backupService.getBackupStats();
        
        // Assert
        assertNotNull(stats);
        assertEquals(2, stats.getTotalBackups());
        assertEquals(1, stats.getCompletedBackups());
        assertEquals(1, stats.getFailedBackups());
        assertEquals(1, stats.getAutomaticBackups());
        assertEquals(1, stats.getManualBackups());
        assertEquals(1024000L, stats.getTotalBackupSizeBytes());
        assertEquals(2, stats.getTotalRestores());
    }
    
    @Test
    void testExportBackup() {
        // Arrange
        when(backupRepository.findById(1L)).thenReturn(Optional.of(testBackup));
        when(dockerService.runCommand(anyString())).thenReturn("Process exited with code: 0");
        
        // Act
        boolean result = backupService.exportBackup(1L, "/export/test-backup.tar.gz");
        
        // Assert
        assertTrue(result);
    }
    
    @Test
    void testImportBackup() {
        // Arrange
        when(clusterRepository.findById(1L)).thenReturn(Optional.of(testCluster));
        when(backupRepository.save(any(ClusterBackup.class))).thenReturn(testBackup);
        when(dockerService.runCommand(anyString())).thenReturn("Process exited with code: 0");
        
        // Act
        ClusterBackup result = backupService.importBackup("/import/test-backup.tar.gz", 1L);
        
        // Assert
        assertNotNull(result);
        assertEquals(ClusterBackup.BackupType.FULL, result.getBackupType());
        assertEquals("Backup importado de /import/test-backup.tar.gz", result.getDescription());
        assertFalse(result.isAutomatic());
        verify(backupRepository).save(any(ClusterBackup.class));
    }
}
