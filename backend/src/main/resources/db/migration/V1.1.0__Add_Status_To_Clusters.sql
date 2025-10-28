-- ============================================
-- MIGRAÇÃO: Adicionar coluna status aos clusters
-- ClusterForge v1.1.0
-- ============================================

-- Adiciona coluna status na tabela clusters
ALTER TABLE clusters 
ADD COLUMN IF NOT EXISTS status VARCHAR(50) DEFAULT 'CREATED';

-- Atualiza status dos clusters existentes baseado no health status
UPDATE clusters c
LEFT JOIN cluster_health_status hs ON c.id = hs.cluster_id
SET c.status = CASE 
    WHEN hs.current_state = 'HEALTHY' THEN 'RUNNING'
    WHEN hs.current_state = 'FAILED' THEN 'FAILED'
    WHEN hs.container_status LIKE '%Exit%' THEN 'STOPPED'
    ELSE 'CREATED'
END
WHERE c.status IS NULL OR c.status = 'CREATED';

-- Índice para melhorar performance de consultas por status
CREATE INDEX IF NOT EXISTS idx_clusters_status ON clusters(status);

-- ============================================
-- FINALIZAÇÃO
-- ============================================

SELECT 'Migration V1.1.0 completed successfully' as status;

