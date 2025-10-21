-- ============================================
-- MIGRAÇÃO: Sistema de Recuperação Ante Falha
-- ClusterForge v1.0.0
-- ============================================

-- Tabela para status de saúde dos clusters
CREATE TABLE IF NOT EXISTS cluster_health_status (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    cluster_id BIGINT NOT NULL,
    current_state VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN',
    last_check_time DATETIME,
    last_successful_check DATETIME,
    consecutive_failures INT DEFAULT 0,
    total_failures INT DEFAULT 0,
    total_recoveries INT DEFAULT 0,
    last_recovery_attempt DATETIME,
    recovery_attempts INT DEFAULT 0,
    max_recovery_attempts INT DEFAULT 3,
    retry_interval_seconds INT DEFAULT 60,
    cooldown_period_seconds INT DEFAULT 300,
    is_monitoring_enabled BOOLEAN DEFAULT TRUE,
    last_alert_time DATETIME,
    alert_threshold_failures INT DEFAULT 3,
    container_status VARCHAR(50),
    application_response_time_ms BIGINT,
    error_message TEXT,
    cpu_usage_percent DOUBLE,
    memory_usage_mb BIGINT,
    disk_usage_mb BIGINT,
    network_rx_mb BIGINT,
    network_tx_mb BIGINT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    FOREIGN KEY (cluster_id) REFERENCES clusters(id) ON DELETE CASCADE,
    INDEX idx_cluster_health_state (current_state),
    INDEX idx_cluster_health_monitoring (is_monitoring_enabled),
    INDEX idx_cluster_health_last_check (last_check_time),
    INDEX idx_cluster_health_failures (consecutive_failures)
);

-- Tabela para métricas de saúde dos clusters
CREATE TABLE IF NOT EXISTS cluster_health_metrics (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    cluster_id BIGINT NOT NULL,
    timestamp DATETIME DEFAULT CURRENT_TIMESTAMP,
    
    -- CPU Metrics
    cpu_usage_percent DOUBLE,
    cpu_limit_cores DOUBLE,
    cpu_throttled_time BIGINT,
    
    -- Memory Metrics
    memory_usage_mb BIGINT,
    memory_limit_mb BIGINT,
    memory_usage_percent DOUBLE,
    memory_cache_mb BIGINT,
    
    -- Disk Metrics
    disk_usage_mb BIGINT,
    disk_limit_mb BIGINT,
    disk_usage_percent DOUBLE,
    disk_read_bytes BIGINT,
    disk_write_bytes BIGINT,
    
    -- Network Metrics
    network_rx_bytes BIGINT,
    network_tx_bytes BIGINT,
    network_rx_packets BIGINT,
    network_tx_packets BIGINT,
    network_limit_mbps BIGINT,
    
    -- Application Metrics
    application_response_time_ms BIGINT,
    application_status_code INT,
    application_uptime_seconds BIGINT,
    application_requests_total BIGINT,
    application_requests_failed BIGINT,
    
    -- Container Metrics
    container_restart_count INT,
    container_uptime_seconds BIGINT,
    container_exit_code INT,
    container_status VARCHAR(50),
    
    FOREIGN KEY (cluster_id) REFERENCES clusters(id) ON DELETE CASCADE,
    INDEX idx_cluster_metrics_timestamp (timestamp),
    INDEX idx_cluster_metrics_cluster_time (cluster_id, timestamp),
    INDEX idx_cluster_metrics_cpu (cpu_usage_percent),
    INDEX idx_cluster_metrics_memory (memory_usage_percent),
    INDEX idx_cluster_metrics_disk (disk_usage_percent)
);

-- Tabela para backups dos clusters
CREATE TABLE IF NOT EXISTS cluster_backups (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    cluster_id BIGINT NOT NULL,
    backup_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS',
    backup_path VARCHAR(500) NOT NULL,
    backup_size_bytes BIGINT,
    compression_ratio DOUBLE,
    description TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    completed_at DATETIME,
    expires_at DATETIME,
    checksum VARCHAR(64),
    is_automatic BOOLEAN DEFAULT FALSE,
    restore_count INT DEFAULT 0,
    last_restore_at DATETIME,
    error_message TEXT,
    
    -- Configurações de backup
    auto_backup_enabled BOOLEAN DEFAULT TRUE,
    backup_interval_hours INT DEFAULT 24,
    retention_days INT DEFAULT 30,
    max_backups INT DEFAULT 10,
    
    FOREIGN KEY (cluster_id) REFERENCES clusters(id) ON DELETE CASCADE,
    INDEX idx_backup_cluster (cluster_id),
    INDEX idx_backup_status (status),
    INDEX idx_backup_type (backup_type),
    INDEX idx_backup_created (created_at),
    INDEX idx_backup_expires (expires_at),
    INDEX idx_backup_automatic (is_automatic)
);

-- Tabela para alertas dos clusters
CREATE TABLE IF NOT EXISTS cluster_alerts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    cluster_id BIGINT NOT NULL,
    alert_type VARCHAR(50) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    title VARCHAR(200) NOT NULL,
    message TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    resolved_at DATETIME,
    resolved_by VARCHAR(100),
    resolution_message TEXT,
    acknowledged_at DATETIME,
    acknowledged_by VARCHAR(100),
    escalated_at DATETIME,
    escalation_level INT DEFAULT 0,
    notification_sent BOOLEAN DEFAULT FALSE,
    notification_channels TEXT,
    metadata TEXT,
    rule_name VARCHAR(100),
    metric_value VARCHAR(100),
    threshold_value VARCHAR(100),
    consecutive_violations INT DEFAULT 1,
    cooldown_until DATETIME,
    
    FOREIGN KEY (cluster_id) REFERENCES clusters(id) ON DELETE CASCADE,
    INDEX idx_alert_cluster (cluster_id),
    INDEX idx_alert_severity (severity),
    INDEX idx_alert_status (status),
    INDEX idx_alert_created (created_at),
    INDEX idx_alert_resolved (resolved_at),
    INDEX idx_alert_type (alert_type)
);

-- Tabela para eventos de saúde dos clusters
CREATE TABLE IF NOT EXISTS cluster_health_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    cluster_health_id BIGINT NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    event_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    message TEXT,
    details TEXT,
    
    FOREIGN KEY (cluster_health_id) REFERENCES cluster_health_status(id) ON DELETE CASCADE,
    INDEX idx_event_health (cluster_health_id),
    INDEX idx_event_type (event_type),
    INDEX idx_event_time (event_time)
);

-- ============================================
-- ÍNDICES ADICIONAIS PARA PERFORMANCE
-- ============================================

-- Índices compostos para consultas frequentes
CREATE INDEX idx_health_cluster_state_monitoring ON cluster_health_status (cluster_id, current_state, is_monitoring_enabled);
CREATE INDEX idx_health_failures_monitoring ON cluster_health_status (consecutive_failures, is_monitoring_enabled);
CREATE INDEX idx_health_recovery_attempts ON cluster_health_status (recovery_attempts, max_recovery_attempts);

CREATE INDEX idx_metrics_cluster_timestamp ON cluster_health_metrics (cluster_id, timestamp DESC);
CREATE INDEX idx_metrics_high_cpu ON cluster_health_metrics (cpu_usage_percent DESC, timestamp DESC);
CREATE INDEX idx_metrics_high_memory ON cluster_health_metrics (memory_usage_percent DESC, timestamp DESC);
CREATE INDEX idx_metrics_high_disk ON cluster_health_metrics (disk_usage_percent DESC, timestamp DESC);

CREATE INDEX idx_backup_cluster_status ON cluster_backups (cluster_id, status, created_at DESC);
CREATE INDEX idx_backup_expired ON cluster_backups (expires_at, status);
CREATE INDEX idx_backup_automatic_interval ON cluster_backups (is_automatic, created_at DESC);

CREATE INDEX idx_alert_cluster_severity ON cluster_alerts (cluster_id, severity, status);
CREATE INDEX idx_alert_active_critical ON cluster_alerts (status, severity, created_at DESC);
CREATE INDEX idx_alert_unresolved ON cluster_alerts (resolved_at, status);

-- ============================================
-- TRIGGERS PARA AUDITORIA E MANUTENÇÃO
-- ============================================

-- Trigger para atualizar updated_at automaticamente
DELIMITER $$
CREATE TRIGGER tr_cluster_health_status_updated 
    BEFORE UPDATE ON cluster_health_status 
    FOR EACH ROW 
BEGIN 
    SET NEW.updated_at = CURRENT_TIMESTAMP; 
END$$
DELIMITER ;

-- Trigger para limpeza automática de métricas antigas (opcional)
DELIMITER $$
CREATE EVENT IF NOT EXISTS ev_cleanup_old_metrics
ON SCHEDULE EVERY 1 DAY
STARTS CURRENT_TIMESTAMP
DO
BEGIN
    -- Remove métricas mais antigas que 30 dias
    DELETE FROM cluster_health_metrics 
    WHERE timestamp < DATE_SUB(NOW(), INTERVAL 30 DAY);
    
    -- Remove eventos de saúde mais antigos que 7 dias
    DELETE FROM cluster_health_events 
    WHERE event_time < DATE_SUB(NOW(), INTERVAL 7 DAY);
END$$
DELIMITER ;

-- ============================================
-- VIEWS PARA CONSULTAS FREQUENTES
-- ============================================

-- View para clusters com problemas de saúde
CREATE OR REPLACE VIEW v_clusters_with_issues AS
SELECT 
    c.id as cluster_id,
    c.name as cluster_name,
    c.port,
    hs.current_state,
    hs.consecutive_failures,
    hs.last_check_time,
    hs.error_message,
    hs.cpu_usage_percent,
    hs.memory_usage_mb,
    hs.disk_usage_mb
FROM clusters c
JOIN cluster_health_status hs ON c.id = hs.cluster_id
WHERE hs.is_monitoring_enabled = TRUE 
  AND hs.current_state IN ('UNHEALTHY', 'FAILED', 'UNKNOWN')
ORDER BY hs.consecutive_failures DESC, hs.last_check_time DESC;

-- View para estatísticas de saúde do sistema
CREATE OR REPLACE VIEW v_system_health_stats AS
SELECT 
    COUNT(*) as total_clusters,
    SUM(CASE WHEN current_state = 'HEALTHY' THEN 1 ELSE 0 END) as healthy_clusters,
    SUM(CASE WHEN current_state = 'UNHEALTHY' THEN 1 ELSE 0 END) as unhealthy_clusters,
    SUM(CASE WHEN current_state = 'FAILED' THEN 1 ELSE 0 END) as failed_clusters,
    SUM(CASE WHEN current_state = 'UNKNOWN' THEN 1 ELSE 0 END) as unknown_clusters,
    SUM(CASE WHEN current_state = 'RECOVERING' THEN 1 ELSE 0 END) as recovering_clusters,
    AVG(application_response_time_ms) as avg_response_time_ms,
    SUM(total_failures) as total_failures_last_24h,
    SUM(total_recoveries) as total_recoveries_last_24h
FROM cluster_health_status
WHERE is_monitoring_enabled = TRUE;

-- View para estatísticas de backup
CREATE OR REPLACE VIEW v_backup_stats AS
SELECT 
    COUNT(*) as total_backups,
    SUM(CASE WHEN status = 'COMPLETED' THEN 1 ELSE 0 END) as completed_backups,
    SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END) as failed_backups,
    SUM(CASE WHEN is_automatic = TRUE THEN 1 ELSE 0 END) as automatic_backups,
    SUM(CASE WHEN is_automatic = FALSE THEN 1 ELSE 0 END) as manual_backups,
    SUM(backup_size_bytes) as total_backup_size_bytes,
    AVG(backup_size_bytes) as avg_backup_size_bytes,
    SUM(restore_count) as total_restores,
    SUM(CASE WHEN created_at >= DATE_SUB(NOW(), INTERVAL 24 HOUR) THEN 1 ELSE 0 END) as backups_last_24h,
    SUM(CASE WHEN created_at >= DATE_SUB(NOW(), INTERVAL 7 DAY) THEN 1 ELSE 0 END) as backups_last_7d
FROM cluster_backups;

-- ============================================
-- PROCEDURES PARA OPERAÇÕES COMUNS
-- ============================================

-- Procedure para criar backup automático
DELIMITER $$
CREATE PROCEDURE sp_create_automatic_backup(IN p_cluster_id BIGINT)
BEGIN
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;
    
    START TRANSACTION;
    
    INSERT INTO cluster_backups (
        cluster_id, 
        backup_type, 
        description, 
        is_automatic,
        status
    ) VALUES (
        p_cluster_id, 
        'INCREMENTAL', 
        'Backup automático', 
        TRUE,
        'IN_PROGRESS'
    );
    
    COMMIT;
END$$
DELIMITER ;

-- Procedure para limpeza de backups antigos
DELIMITER $$
CREATE PROCEDURE sp_cleanup_old_backups()
BEGIN
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;
    
    START TRANSACTION;
    
    -- Remove backups expirados
    DELETE FROM cluster_backups 
    WHERE expires_at < NOW() 
      AND status IN ('COMPLETED', 'FAILED');
    
    COMMIT;
END$$
DELIMITER ;

-- ============================================
-- DADOS INICIAIS (SEED DATA)
-- ============================================

-- Inserir configurações padrão para clusters existentes
INSERT IGNORE INTO cluster_health_status (
    cluster_id,
    current_state,
    is_monitoring_enabled,
    max_recovery_attempts,
    retry_interval_seconds,
    cooldown_period_seconds,
    alert_threshold_failures
)
SELECT 
    id,
    'UNKNOWN',
    TRUE,
    3,
    60,
    300,
    3
FROM clusters
WHERE id NOT IN (SELECT cluster_id FROM cluster_health_status);

-- ============================================
-- COMENTÁRIOS E DOCUMENTAÇÃO
-- ============================================

-- Comentários nas tabelas
ALTER TABLE cluster_health_status COMMENT = 'Status de saúde e monitoramento dos clusters';
ALTER TABLE cluster_health_metrics COMMENT = 'Métricas detalhadas de recursos dos clusters';
ALTER TABLE cluster_backups COMMENT = 'Backups e políticas de retenção dos clusters';
ALTER TABLE cluster_alerts COMMENT = 'Alertas e notificações do sistema de monitoramento';
ALTER TABLE cluster_health_events COMMENT = 'Histórico de eventos de saúde dos clusters';

-- Comentários nas colunas principais
ALTER TABLE cluster_health_status MODIFY COLUMN current_state VARCHAR(20) COMMENT 'Estado atual: HEALTHY, UNHEALTHY, FAILED, UNKNOWN, RECOVERING';
ALTER TABLE cluster_backups MODIFY COLUMN backup_type VARCHAR(20) COMMENT 'Tipo: FULL, INCREMENTAL, CONFIG_ONLY, DATA_ONLY';
ALTER TABLE cluster_backups MODIFY COLUMN status VARCHAR(20) COMMENT 'Status: IN_PROGRESS, COMPLETED, FAILED, CORRUPTED, EXPIRED';
ALTER TABLE cluster_alerts MODIFY COLUMN severity VARCHAR(20) COMMENT 'Severidade: CRITICAL, WARNING, INFO, RECOVERY';
ALTER TABLE cluster_alerts MODIFY COLUMN status VARCHAR(20) COMMENT 'Status: ACTIVE, RESOLVED, SUPPRESSED, EXPIRED';

-- ============================================
-- FINALIZAÇÃO
-- ============================================

-- Verificar se todas as tabelas foram criadas
SELECT 'Migration completed successfully' as status,
       COUNT(*) as tables_created
FROM information_schema.tables 
WHERE table_schema = DATABASE() 
  AND table_name IN ('cluster_health_status', 'cluster_health_metrics', 'cluster_backups', 'cluster_alerts', 'cluster_health_events');
