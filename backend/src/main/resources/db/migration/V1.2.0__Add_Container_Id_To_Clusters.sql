-- ============================================
-- MIGRAÇÃO: Adicionar coluna container_id aos clusters
-- ClusterForge v1.2.0
-- ============================================

-- Adiciona coluna container_id na tabela clusters
ALTER TABLE clusters 
ADD COLUMN IF NOT EXISTS container_id VARCHAR(64);

-- Comentário explicativo
COMMENT ON COLUMN clusters.container_id IS 'ID do container Docker para operações precisas';

-- Índice para melhorar performance de consultas por container_id
CREATE INDEX IF NOT EXISTS idx_clusters_container_id ON clusters(container_id);

-- ============================================
-- FINALIZAÇÃO
-- ============================================

SELECT 'Migration V1.2.0 completed successfully' as status;

