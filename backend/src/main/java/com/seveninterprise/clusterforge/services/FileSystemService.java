package com.seveninterprise.clusterforge.services;

import com.seveninterprise.clusterforge.exceptions.ClusterException;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Implementação de serviços de sistema de arquivos
 * 
 * Responsável por operações de:
 * - Criação e gerenciamento de diretórios de clusters
 * - Cópia de templates e scripts
 * - Configuração de permissões
 * - Validação de arquivos
 */
@Service
public class FileSystemService implements IFileSystemService {
    
    @Override
    public String createClusterDirectory(String clusterName, String basePath) {
        try {
            String clusterPath = basePath + "/" + clusterName;
            
            File directory = new File(clusterPath);
            if (!directory.exists()) {
                boolean created = directory.mkdirs();
                if (!created) {
                    throw new ClusterException("Erro ao criar diretório do cluster: " + clusterPath);
                }
            }
            
            return clusterPath;
        } catch (Exception e) {
            throw new ClusterException("Erro ao criar diretório do cluster: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void copyTemplateFiles(String templatePath, String targetPath) {
        try {
            Path sourcePath = Paths.get(templatePath);
            Path targetPathObj = Paths.get(targetPath);
            
            if (!Files.exists(sourcePath)) {
                throw new ClusterException("Template não encontrado: " + templatePath);
            }
            
            Files.walk(sourcePath)
                .forEach(source -> {
                    Path target = targetPathObj.resolve(sourcePath.relativize(source));
                    try {
                        if (Files.isDirectory(source)) {
                            Files.createDirectories(target);
                            setFilePermissions(target, true);
                        } else {
                            Files.copy(source, target);
                            setFilePermissions(target, false);
                        }
                    } catch (Exception e) {
                        throw new ClusterException("Erro ao copiar arquivos: " + e.getMessage(), e);
                    }
                });
        } catch (ClusterException e) {
            throw e;
        } catch (Exception e) {
            throw new ClusterException("Erro ao copiar template: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void copySystemScripts(String scriptsBasePath, String clusterPath) {
        try {
            Path scriptsSourcePath = Paths.get(scriptsBasePath);
            
            if (!Files.exists(scriptsSourcePath)) {
                System.err.println("AVISO: Diretório de scripts não encontrado: " + scriptsBasePath);
                return;
            }
            
            Files.walk(scriptsSourcePath)
                .filter(Files::isRegularFile)
                .forEach(source -> {
                    try {
                        Path target = Paths.get(clusterPath).resolve(source.getFileName());
                        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                        
                        // Torna o script executável
                        setFilePermissions(target, false);
                        
                        System.out.println("✓ Script copiado: " + source.getFileName());
                    } catch (Exception e) {
                        System.err.println("Erro ao copiar script " + source.getFileName() + ": " + e.getMessage());
                    }
                });
                
        } catch (Exception e) {
            System.err.println("Erro ao copiar scripts do sistema: " + e.getMessage());
            // Não lança exceção - scripts são opcionais
        }
    }
    
    @Override
    public void removeDirectory(String path) {
        try {
            Path directoryPath = Paths.get(path);
            if (Files.exists(directoryPath)) {
                // Remove recursivamente (importante para diretórios não vazios)
                Files.walk(directoryPath)
                    .sorted((a, b) -> b.compareTo(a)) // Ordem reversa: arquivos antes de diretórios
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (Exception e) {
                            System.err.println("Warning: Failed to remove " + p + ": " + e.getMessage());
                        }
                    });
            }
        } catch (Exception e) {
            System.err.println("Warning: Failed to remove directory " + path + ": " + e.getMessage());
        }
    }
    
    @Override
    public boolean directoryExists(String path) {
        File directory = new File(path);
        return directory.exists() && directory.isDirectory();
    }
    
    @Override
    public String readFile(String filePath) {
        try {
            byte[] bytes = Files.readAllBytes(Paths.get(filePath));
            return new String(bytes);
        } catch (Exception e) {
            throw new ClusterException("Erro ao ler arquivo: " + filePath + " - " + e.getMessage(), e);
        }
    }
    
    @Override
    public void writeFile(String filePath, String content) {
        try {
            Files.write(Paths.get(filePath), content.getBytes());
        } catch (Exception e) {
            throw new ClusterException("Erro ao escrever arquivo: " + filePath + " - " + e.getMessage(), e);
        }
    }
    
    @Override
    public void setFilePermissions(Path path, boolean isDirectory) {
        try {
            if (isDirectory) {
                // Permissões para diretórios: rwxrwxr-x (775)
                Set<PosixFilePermission> dirPermissions = Stream.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE,
                    PosixFilePermission.GROUP_READ,
                    PosixFilePermission.GROUP_WRITE,
                    PosixFilePermission.GROUP_EXECUTE,
                    PosixFilePermission.OTHERS_READ,
                    PosixFilePermission.OTHERS_EXECUTE
                ).collect(Collectors.toSet());
                
                Files.setPosixFilePermissions(path, dirPermissions);
            } else {
                // Permissões para arquivos normais: rw-rw-r-- (664)
                Set<PosixFilePermission> filePermissions = Stream.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.GROUP_READ,
                    PosixFilePermission.GROUP_WRITE,
                    PosixFilePermission.OTHERS_READ
                ).collect(Collectors.toSet());
                
                Files.setPosixFilePermissions(path, filePermissions);
            }
        } catch (Exception e) {
            // Em sistemas Windows ou quando não é possível configurar permissões POSIX,
            // apenas loga mas não lança exceção
            System.err.println("Warning: Could not set POSIX permissions for " + path + ": " + e.getMessage());
        }
    }
}

