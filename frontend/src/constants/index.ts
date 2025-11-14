/**
 * Constantes centralizadas da aplicação
 */

// ============================================
// CONFIGURAÇÕES
// ============================================
export const STORAGE_KEYS = {
  TOKEN: 'clusterforge_token',
  REFRESH_TOKEN: 'clusterforge_refresh_token',
  USER: 'clusterforge_user',
  TOKEN_EXPIRES_AT: 'clusterforge_token_expires_at',
  THEME: 'clusterforge_theme',
} as const;

// ============================================
// STATUS MAPPING
// ============================================
export const CLUSTER_STATUS_MAP: Record<string, 'running' | 'stopped' | 'restarting' | 'error'> = {
  'CREATED': 'running',
  'STARTING': 'restarting',
  'RUNNING': 'running',
  'STOPPING': 'restarting',
  'STOPPED': 'stopped',
  'FAILED': 'error',
  'RESTARTING': 'restarting',
} as const;

// ============================================
// TEMPLATE ICONS
// ============================================
export const TEMPLATE_ICON_MAP: Record<string, string> = {
  'test-alpine': 'Package',
  'webserver-php': 'Code',
  'wordpress': 'FileText',
  'nginx': 'Globe',
  'apache': 'Globe',
  'mysql': 'Database',
  'postgres': 'Database',
  'redis': 'CircuitBoard',
  'nodejs': 'Zap',
  'python': 'Code',
  'java': 'Box',
  'minecraft': 'Gamepad2',
  'docker': 'Layers',
  'kubernetes': 'Rocket',
} as const;

// ============================================
// RECURSOS PADRÃO POR TEMPLATE
// ============================================
export interface DefaultResources {
  cpu: number; // em percentual (5-100%)
  ram: number; // em GB
  disk: number; // em GB
}

export const TEMPLATE_DEFAULT_RESOURCES: Record<string, DefaultResources> = {
  'test-alpine': { cpu: 15, ram: 0.5, disk: 2 },
  'webserver-php': { cpu: 30, ram: 2, disk: 5 },
  'wordpress': { cpu: 30, ram: 2, disk: 5 },
  'minecraft': { cpu: 50, ram: 4, disk: 10 },
  'database-mysql': { cpu: 40, ram: 4, disk: 20 },
  'database-postgres': { cpu: 40, ram: 4, disk: 20 },
  'nodejs': { cpu: 25, ram: 1, disk: 3 },
  'api-node': { cpu: 25, ram: 1, disk: 3 },
  'nginx': { cpu: 20, ram: 1, disk: 2 },
  'apache': { cpu: 20, ram: 1, disk: 2 },
  'redis': { cpu: 15, ram: 1, disk: 1 },
  'default': { cpu: 25, ram: 2, disk: 10 },
} as const;

// ============================================
// TEMPLATE NAME FORMATTING
// ============================================
export const TEMPLATE_NAME_FORMAT: Record<string, string> = {
  'webserver-php': 'Servidor Web PHP',
  'webserver-node': 'Servidor Web Node.js',
  'webserver': 'Servidor Web',
  'minecraft': 'Servidor Minecraft',
  'wordpress': 'Blog WordPress',
  'database-mysql': 'Banco MySQL',
  'database-postgres': 'Banco PostgreSQL',
  'redis': 'Cache Redis',
  'api-node': 'API Node.js',
  'api-python': 'API Python',
} as const;

// ============================================
// VALIDAÇÕES
// ============================================
export const VALIDATION_LIMITS = {
  CPU: { min: 5, max: 100 },
  RAM: { min: 0.5, max: 32 },
  DISK: { min: 1, max: 500 },
  PORT: { min: 1, max: 65535 },
} as const;

// ============================================
// PAGINAÇÃO E LIMITES
// ============================================
export const PAGINATION = {
  DEFAULT_PAGE_SIZE: 10,
  MAX_PAGE_SIZE: 100,
} as const;

// ============================================
// WEBSOCKET
// ============================================
export const WEBSOCKET_CONFIG = {
  RECONNECT_DELAY: 3000,
  MAX_RECONNECT_ATTEMPTS: 5,
  HEARTBEAT_INTERVAL: 4000,
} as const;

// ============================================
// TIMEOUTS
// ============================================
export const TIMEOUTS = {
  API_REQUEST: 30000, // 30 segundos
  CLUSTER_START_POLL: 1500, // 1.5 segundos
  CLUSTER_START_MAX_ATTEMPTS: 15,
  CLUSTER_STOP_POLL: 1000, // 1 segundo
  CLUSTER_STOP_MAX_ATTEMPTS: 20,
  CLUSTER_CREATE_POLL: 12000, // 2 segundos
  CLUSTER_CREATE_MAX_ATTEMPTS: 60, // 2 minutos (60 * 2s)
} as const;

// ============================================
// GRÁFICOS E VISUALIZAÇÃO
// ============================================
export const CHART_CONFIG = {
  MAX_DATA_POINTS: 100,
  INITIAL_VISIBLE_POINTS: 10,
  AUTO_ZOOM_INTERVAL: 10000, // 10 segundos
  DATA_UPDATE_THRESHOLD: 0.01, // Mudança mínima para adicionar novo ponto
} as const;


