package com.seveninterprise.clusterforge.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

/**
 * Configuração que carrega propriedades do arquivo config.json
 */
@Configuration
@PropertySource(value = "classpath:config.json", factory = JsonPropertySourceFactory.class)
public class JsonConfigLoader {
    
    // Esta classe apenas registra o PropertySource do JSON
    // As propriedades ficam disponíveis através do @Value e @ConfigurationProperties
}
