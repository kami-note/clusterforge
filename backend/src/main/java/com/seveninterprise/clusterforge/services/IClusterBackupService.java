package com.seveninterprise.clusterforge.services;

import com.seveninterprise.clusterforge.model.Cluster;
import com.seveninterprise.clusterforge.model.ClusterBackup;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Interface para serviços de backup e recuperação de clusters
 * 
 * Sistema de Backup e Recuperação:
 * - Backup automático periódico de dados críticos
 * - Backup sob demanda
 * - Recuperação pontual e completa
 * - Compressão e otimização de backups
 * - Retenção configurável de backups
 * - Verificação de integridade
 * 
 * Tipos de Backup:
 * - FULL: Backup completo do cluster (código + dados + configuração)
 * - INCREMENTAL: Apenas mudanças desde último backup
 * - CONFIG_ONLY: Apenas configurações (docker-compose.yml, etc.)
 * - DATA_ONLY: Apenas dados da aplicação
 */
public interface IClusterBackupService {
    
    /**
     * Cria backup completo de um cluster
     * 
     * Processo:
     * 1. Para container se estiver rodando
     * 2. Cria snapshot dos volumes de dados
     * 3. Copia arquivos de configuração
     * 4. Comprime e armazena backup
     * 5. Verifica integridade do backup
     * 6. Reinicia container se estava rodando
     * 
     * @param clusterId ID do cluster
     * @param backupType Tipo de backup (FULL, INCREMENTAL, CONFIG_ONLY, DATA_ONLY)
     * @param description Descrição opcional do backup
     * @return Informações do backup criado
     */
    ClusterBackup createBackup(Long clusterId, ClusterBackup.BackupType backupType, String description);
    
    /**
     * Cria backup automático de todos os clusters ativos
     * 
     * Executa backup incremental para clusters que:
     * - Estão rodando
     * - Tiveram mudanças desde último backup
     * - Não estão em processo de backup
     * 
     * @return Número de backups criados
     */
    int createAutomaticBackups();
    
    /**
     * Restaura cluster a partir de um backup
     * 
     * Processo:
     * 1. Valida integridade do backup
     * 2. Para container atual
     * 3. Restaura dados do backup
     * 4. Reinicia container
     * 5. Verifica se restauração foi bem-sucedida
     * 
     * @param backupId ID do backup a restaurar
     * @param clusterId ID do cluster (opcional, usa cluster original se null)
     * @return true se restauração foi bem-sucedida
     */
    boolean restoreFromBackup(Long backupId, Long clusterId);
    
    /**
     * Lista backups de um cluster
     * 
     * @param clusterId ID do cluster
     * @return Lista de backups ordenada por data (mais recente primeiro)
     */
    List<ClusterBackup> listClusterBackups(Long clusterId);
    
    /**
     * Lista todos os backups do sistema
     * 
     * @return Lista de todos os backups
     */
    List<ClusterBackup> listAllBackups();
    
    /**
     * Obtém informações detalhadas de um backup
     * 
     * @param backupId ID do backup
     * @return Informações do backup
     */
    Optional<ClusterBackup> getBackupById(Long backupId);
    
    /**
     * Verifica integridade de um backup
     * 
     * @param backupId ID do backup
     * @return true se backup está íntegro
     */
    boolean verifyBackupIntegrity(Long backupId);
    
    /**
     * Remove backup antigo
     * 
     * @param backupId ID do backup
     * @return true se remoção foi bem-sucedida
     */
    boolean deleteBackup(Long backupId);
    
    /**
     * Limpa backups antigos baseado na política de retenção
     * 
     * Remove backups que:
     * - Excedem período de retenção configurado
     * - São redundantes (múltiplos backups do mesmo dia)
     * - Falharam na verificação de integridade
     * 
     * @return Número de backups removidos
     */
    int cleanupOldBackups();
    
    /**
     * Configura política de backup para um cluster
     * 
     * @param clusterId ID do cluster
     * @param autoBackupEnabled Se backup automático está habilitado
     * @param backupIntervalHours Intervalo entre backups automáticos (horas)
     * @param retentionDays Dias para manter backups
     * @param maxBackups Número máximo de backups a manter
     */
    void configureBackupPolicy(Long clusterId, boolean autoBackupEnabled, 
                             int backupIntervalHours, int retentionDays, int maxBackups);
    
    /**
     * Obtém estatísticas de backup do sistema
     * 
     * @return Estatísticas agregadas
     */
    ClusterBackup.BackupStats getBackupStats();
    
    /**
     * Exporta backup para arquivo externo
     * 
     * @param backupId ID do backup
     * @param exportPath Caminho para exportar
     * @return true se exportação foi bem-sucedida
     */
    boolean exportBackup(Long backupId, String exportPath);
    
    /**
     * Importa backup de arquivo externo
     * 
     * @param importPath Caminho do arquivo de backup
     * @param clusterId ID do cluster de destino
     * @return Informações do backup importado
     */
    ClusterBackup importBackup(String importPath, Long clusterId);
}
