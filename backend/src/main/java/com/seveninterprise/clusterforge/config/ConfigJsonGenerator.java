package com.seveninterprise.clusterforge.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Gerador de JSON de configuração a partir dos arquivos application.properties
 * Este utilitário é executado durante o build do Maven para gerar um arquivo JSON
 * com todas as configurações do sistema.
 */
public class ConfigJsonGenerator {
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Uso: java ConfigJsonGenerator <output-directory>");
            System.exit(1);
        }
        
        String outputDir = args[0];
        System.out.println("Gerando arquivo de configuração JSON...");
        
        try {
            // Ler propriedades do application.properties principal
            Properties mainProperties = loadProperties("application.properties");
            
            // Ler propriedades do application-dev.properties
            Properties devProperties = loadProperties("application-dev.properties");
            
            // Criar objeto JSON com todas as configurações
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode configJson = mapper.createObjectNode();
            
            // Adicionar configurações principais
            addPropertiesToJson(configJson, mainProperties, "main");
            
            // Adicionar configurações de desenvolvimento
            addPropertiesToJson(configJson, devProperties, "dev");
            
            // Adicionar metadados
            configJson.put("generatedAt", java.time.Instant.now().toString());
            configJson.put("version", "0.0.1-SNAPSHOT");
            configJson.put("description", "ClusterForge Application Configuration");
            
            // Escrever arquivo JSON
            Path outputPath = Paths.get(outputDir, "config.json");
            Files.createDirectories(outputPath.getParent());
            
            try (FileWriter writer = new FileWriter(outputPath.toFile())) {
                mapper.writerWithDefaultPrettyPrinter().writeValue(writer, configJson);
            }
            
            System.out.println("Arquivo de configuração gerado com sucesso: " + outputPath);
            
        } catch (Exception e) {
            System.err.println("Erro ao gerar arquivo de configuração: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static Properties loadProperties(String fileName) {
        Properties properties = new Properties();
        
        try (InputStream input = ConfigJsonGenerator.class.getClassLoader().getResourceAsStream(fileName)) {
            if (input != null) {
                properties.load(input);
                System.out.println("Carregadas " + properties.size() + " propriedades de " + fileName);
            } else {
                System.out.println("Arquivo " + fileName + " não encontrado");
            }
        } catch (IOException e) {
            System.err.println("Erro ao carregar " + fileName + ": " + e.getMessage());
        }
        
        return properties;
    }
    
    private static void addPropertiesToJson(ObjectNode jsonNode, Properties properties, String section) {
        ObjectNode sectionNode = jsonNode.putObject(section);
        
        for (String key : properties.stringPropertyNames()) {
            String value = properties.getProperty(key);
            
            // Tentar converter para número se possível
            if (isNumeric(value)) {
                try {
                    if (value.contains(".")) {
                        sectionNode.put(key, Double.parseDouble(value));
                    } else {
                        sectionNode.put(key, Long.parseLong(value));
                    }
                } catch (NumberFormatException e) {
                    sectionNode.put(key, value);
                }
            } else if (isBoolean(value)) {
                sectionNode.put(key, Boolean.parseBoolean(value));
            } else {
                sectionNode.put(key, value);
            }
        }
    }
    
    private static boolean isNumeric(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        try {
            Double.parseDouble(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    private static boolean isBoolean(String str) {
        return "true".equalsIgnoreCase(str) || "false".equalsIgnoreCase(str);
    }
}
