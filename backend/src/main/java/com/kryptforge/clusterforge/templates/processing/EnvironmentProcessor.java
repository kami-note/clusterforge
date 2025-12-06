package com.kryptforge.clusterforge.templates.processing;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

/**
 * Responsável por processar variáveis de ambiente.
 * 
 * <p>
 * Extraído do TemplateInstantiationService para seguir SRP.
 * </p>
 */
@Component
public class EnvironmentProcessor {

    /**
     * Mescla variáveis de ambiente do compose com overrides.
     * 
     * @param specEnv     variáveis do compose
     * @param overrideEnv variáveis de override
     * @return mapa mesclado de variáveis
     */
    public Map<String, String> mergeEnvironment(
            Map<String, String> specEnv,
            Map<String, String> overrideEnv) {

        Map<String, String> env = new LinkedHashMap<>();

        if (specEnv != null) {
            env.putAll(specEnv);
        }

        if (!CollectionUtils.isEmpty(overrideEnv)) {
            env.putAll(overrideEnv);
        }

        return env;
    }

    /**
     * Adiciona variáveis de ambiente padrão do sistema.
     * 
     * @param env          mapa de variáveis existentes
     * @param instanceName nome da instância
     * @return mapa atualizado
     */
    public Map<String, String> addSystemEnvironment(
            Map<String, String> env,
            String instanceName) {

        Map<String, String> result = new LinkedHashMap<>(env);

        // Adiciona variáveis de sistema se não existirem
        result.putIfAbsent("CLUSTER_NAME", instanceName);
        result.putIfAbsent("TZ", System.getProperty("user.timezone", "UTC"));

        return result;
    }
}
