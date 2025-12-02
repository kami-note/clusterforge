package com.kryptforge.clusterforge.clusters;

/**
 * Constantes centralizadas para configurações de clusters.
 * Evita magic numbers espalhados pelo código.
 */
public final class ClusterConstants {

	private ClusterConstants() {
		// Classe de constantes - não deve ser instanciada
	}

	// ============================================
	// Timeouts de Container (em segundos)
	// ============================================

	/** Timeout padrão para parar um container */
	public static final int DEFAULT_STOP_TIMEOUT_SECONDS = 10;

	/** Timeout máximo para parar um container */
	public static final int MAX_STOP_TIMEOUT_SECONDS = 300;

	/** Timeout para operações de inspeção de container */
	public static final int INSPECT_TIMEOUT_SECONDS = 30;

	// ============================================
	// Delays e Intervalos (em milissegundos)
	// ============================================

	/** Delay entre parar e iniciar durante restart */
	public static final long RESTART_DELAY_MS = 2000;

	/** Intervalo de reconexão após erro no listener de eventos */
	public static final long EVENT_RECONNECT_DELAY_MS = 5000;

	/** Intervalo de coleta de métricas */
	public static final long METRICS_COLLECTION_INTERVAL_MS = 5000;

	/** Intervalo de flush do buffer de métricas */
	public static final long METRICS_FLUSH_INTERVAL_MS = 5000;

	/** Delay entre flushes durante shutdown */
	public static final long SHUTDOWN_FLUSH_DELAY_MS = 500;

	/** Delay inicial para coleta de métricas agregadas */
	public static final long INITIAL_METRICS_DELAY_MS = 1000;

	// ============================================
	// Tamanhos de Buffer e Batch
	// ============================================

	/** Tamanho do batch para inserção de métricas */
	public static final int METRICS_BATCH_SIZE = 50;

	/** Capacidade máxima do buffer de métricas */
	public static final int METRICS_BUFFER_MAX_SIZE = 1000;

	/** Número máximo de flushes durante shutdown */
	public static final int MAX_SHUTDOWN_FLUSHES = 10;

	// ============================================
	// Timeouts de Shutdown (em segundos)
	// ============================================

	/** Timeout para aguardar término do executor */
	public static final int EXECUTOR_SHUTDOWN_TIMEOUT_SECONDS = 30;

	/** Timeout adicional após shutdownNow */
	public static final int EXECUTOR_FORCE_SHUTDOWN_TIMEOUT_SECONDS = 10;

	/** Timeout para scheduler de flush */
	public static final int FLUSH_SCHEDULER_SHUTDOWN_TIMEOUT_SECONDS = 5;

	/** Timeout adicional para scheduler após shutdownNow */
	public static final int FLUSH_SCHEDULER_FORCE_SHUTDOWN_TIMEOUT_SECONDS = 2;

	// ============================================
	// Configurações de Thread Pool
	// ============================================

	/** Número de threads para armazenamento de métricas */
	public static final int METRICS_STORAGE_THREAD_POOL_SIZE = 2;

	// ============================================
	// Retenção de Dados (em dias)
	// ============================================

	/** Dias padrão para retenção de métricas */
	public static final int DEFAULT_METRICS_RETENTION_DAYS = 7;

	/** Dias padrão para retenção de logs */
	public static final int DEFAULT_LOGS_RETENTION_DAYS = 7;

	// ============================================
	// SSE Timeouts (em milissegundos)
	// ============================================

	/** Timeout padrão para SSE de métricas */
	public static final long SSE_METRICS_TIMEOUT_MS = 300_000; // 5 minutos

	/** Timeout padrão para SSE de logs */
	public static final long SSE_LOGS_TIMEOUT_MS = 300_000; // 5 minutos

	// ============================================
	// Portas
	// ============================================

	/** Porta mínima para alocação dinâmica */
	public static final int MIN_DYNAMIC_PORT = 10000;

	/** Porta máxima para alocação dinâmica */
	public static final int MAX_DYNAMIC_PORT = 65535;

	/** Porta padrão do WebDAV no container */
	public static final int WEBDAV_CONTAINER_PORT = 80;

	// ============================================
	// Mensagens de Erro
	// ============================================

	public static final String ERROR_USER_NOT_AUTHENTICATED = "usuário não autenticado";
	public static final String ERROR_CLUSTER_NOT_FOUND = "cluster não encontrado";
	public static final String ERROR_USER_NOT_FOUND = "usuário não encontrado";
	public static final String ERROR_ACCESS_DENIED = "acesso negado: cluster não pertence ao usuário";
	public static final String ERROR_ONLY_ADMIN_CAN_CHANGE_OWNER = "apenas administradores podem alterar o proprietário de um cluster";
	public static final String ERROR_NO_CONTAINER_ID = "Instância não possui containerId";
}

