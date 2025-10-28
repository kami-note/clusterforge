package com.seveninterprise.clusterforge.services;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Interface para operações de sistema de arquivos
 * 
 * Responsabilidades:
 * - Criação de diretórios
 * - Cópia de templates para clusters
 * - Cópia de scripts centralizados
 * - Configuração de permissões de arquivos
 * - Validação de existência de arquivos
 */
public interface IFileSystemService {
    
    /**
     * Cria um diretório para o cluster
     * 
     * @param clusterName Nome do cluster
     * @param basePath Caminho base onde os clusters são armazenados
     * @return Caminho completo do diretório criado
     * @throws IOException Se houver erro ao criar o diretório
     */
    String createClusterDirectory(String clusterName, String basePath) throws IOException;
    
    /**
     * Copia arquivos de um template para o diretório do cluster
     * 
     * @param templatePath Caminho do template a ser copiado
     * @param targetPath Caminho de destino (diretório do cluster)
     * @throws IOException Se houver erro ao copiar arquivos
     */
    void copyTemplateFiles(String templatePath, String targetPath) throws IOException;
    
    /**
     * Copia scripts centralizados para o diretório do cluster
     * 
     * @param scriptsBasePath Caminho dos scripts centralizados
     * @param clusterPath Caminho do cluster
     * @throws IOException Se houver erro ao copiar scripts
     */
    void copySystemScripts(String scriptsBasePath, String clusterPath) throws IOException;
    
    /**
     * Remove um diretório e todo seu conteúdo
     * 
     * @param path Caminho do diretório a ser removido
     * @throws IOException Se houver erro ao remover
     */
    void removeDirectory(String path) throws IOException;
    
    /**
     * Verifica se um diretório existe
     * 
     * @param path Caminho a verificar
     * @return true se existe, false caso contrário
     */
    boolean directoryExists(String path);
    
    /**
     * Lê conteúdo de um arquivo
     * 
     * @param filePath Caminho do arquivo
     * @return Conteúdo do arquivo como String
     * @throws IOException Se houver erro ao ler
     */
    String readFile(String filePath) throws IOException;
    
    /**
     * Escreve conteúdo em um arquivo
     * 
     * @param filePath Caminho do arquivo
     * @param content Conteúdo a ser escrito
     * @throws IOException Se houver erro ao escrever
     */
    void writeFile(String filePath, String content) throws IOException;
    
    /**
     * Configura permissões de arquivo Unix
     * 
     * @param path Caminho do arquivo
     * @param isDirectory true se for diretório, false se for arquivo
     * @throws IOException Se houver erro ao configurar permissões
     */
    void setFilePermissions(Path path, boolean isDirectory) throws IOException;
}

