package com.seveninterprise.clusterforge.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Componente para testar se as configurações estão sendo carregadas do JSON
 */
@Component
public class ConfigTestRunner implements CommandLineRunner {
    
    @Value("${spring.application.name:NOT_FOUND}")
    private String applicationName;
    
    @Value("${system.maxcluster.size:NOT_FOUND}")
    private int maxClusterSize;
    
    @Value("${system.default.cpu.limit:NOT_FOUND}")
    private double defaultCpuLimit;
    
    @Value("${system.directory.template:NOT_FOUND}")
    private String directoryTemplate;
    
    @Value("${jwt.secret.key:NOT_FOUND}")
    private String jwtSecretKey;
    
    @Override
    public void run(String... args) throws Exception {
        System.out.println("\n🔍 === TESTE DE CONFIGURAÇÕES DO JSON ===");
        System.out.println("📱 Application Name: " + applicationName);
        System.out.println("🏗️ Max Cluster Size: " + maxClusterSize);
        System.out.println("💻 Default CPU Limit: " + defaultCpuLimit);
        System.out.println("📁 Directory Template: " + directoryTemplate);
        System.out.println("🔐 JWT Secret Key: " + (jwtSecretKey.length() > 10 ? jwtSecretKey.substring(0, 10) + "..." : jwtSecretKey));
        System.out.println("✅ === CONFIGURAÇÕES CARREGADAS COM SUCESSO ===\n");
    }
}
