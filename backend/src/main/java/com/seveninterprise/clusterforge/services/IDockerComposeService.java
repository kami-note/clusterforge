package com.seveninterprise.clusterforge.services;

import com.seveninterprise.clusterforge.model.Cluster;

/**
 * Interface para manipulação de arquivos Docker Compose
 * 
 * Responsabilidades:
 * - Leitura e escrita de arquivos docker-compose.yml
 * - Modificação de portas e mapeamentos
 * - Configuração de limites de recursos (CPU, memória, disco, rede)
 * - Injeção de scripts e variáveis de ambiente
 * - Configuração de capabilities do Docker
 */
public interface IDockerComposeService {
    
    /**
     * Lê o conteúdo de um arquivo docker-compose.yml
     * 
     * @param composeFilePath Caminho do arquivo docker-compose.yml
     * @return Conteúdo do arquivo
     */
    String readComposeFile(String composeFilePath);
    
    /**
     * Atualiza a configuração do docker-compose.yml do cluster
     * 
     * Modificações aplicadas:
     * - Substitui porta padrão pela porta do cluster
     * - Atualiza nome do container
     * - Adiciona limites de recursos (CPU, memória)
     * - Adiciona variáveis de ambiente
     * - Adiciona capabilities necessárias (NET_ADMIN)
     * - Configura tmpfs para limites de disco
     * 
     * @param composeFilePath Caminho do arquivo docker-compose.yml
     * @param cluster Cluster com configurações a aplicar
     * @return Conteúdo atualizado do arquivo
     */
    String updateComposeFileForCluster(String composeFilePath, Cluster cluster);
    
    /**
     * Extrai a porta padrão de um arquivo docker-compose.yml
     * 
     * @param composeContent Conteúdo do arquivo docker-compose.yml
     * @return Porta padrão encontrada (ex: 8080)
     */
    String extractDefaultPort(String composeContent);
    
    /**
     * Extrai o nome padrão do container
     * 
     * @param composeContent Conteúdo do arquivo docker-compose.yml
     * @return Nome padrão do container (ex: php_web)
     */
    String extractDefaultContainerName(String composeContent);
}

