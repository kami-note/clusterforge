package com.seveninterprise.clusterforge.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.core.io.support.PropertySourceFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Factory para criar PropertySource a partir do arquivo config.json
 */
public class JsonPropertySourceFactory implements PropertySourceFactory {
    
    @Override
    public PropertySource<?> createPropertySource(String name, EncodedResource resource) throws IOException {
        Map<String, Object> properties = new HashMap<>();
        
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode rootNode = mapper.readTree(resource.getInputStream());
            
            // Carregar propriedades da seção 'main' (configurações principais)
            JsonNode mainNode = rootNode.get("main");
            if (mainNode != null) {
                loadNodeProperties(mainNode, properties, "");
            }
            
            // Carregar propriedades da seção 'dev' (configurações de desenvolvimento)
            JsonNode devNode = rootNode.get("dev");
            if (devNode != null) {
                loadNodeProperties(devNode, properties, "dev.");
            }
            
            System.out.println("✅ Carregadas " + properties.size() + " propriedades do config.json");
            
        } catch (Exception e) {
            System.err.println("❌ Erro ao carregar config.json: " + e.getMessage());
            throw new IOException("Falha ao carregar config.json", e);
        }
        
        return new MapPropertySource(name != null ? name : "jsonConfig", properties);
    }
    
    private void loadNodeProperties(JsonNode node, Map<String, Object> properties, String prefix) {
        node.fields().forEachRemaining(entry -> {
            String key = prefix + entry.getKey();
            JsonNode value = entry.getValue();
            
            if (value.isTextual()) {
                properties.put(key, value.asText());
            } else if (value.isNumber()) {
                if (value.isInt()) {
                    properties.put(key, value.asInt());
                } else if (value.isLong()) {
                    properties.put(key, value.asLong());
                } else if (value.isDouble()) {
                    properties.put(key, value.asDouble());
                } else {
                    properties.put(key, value.asText());
                }
            } else if (value.isBoolean()) {
                properties.put(key, value.asBoolean());
            } else {
                properties.put(key, value.asText());
            }
        });
    }
}


