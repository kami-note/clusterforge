package com.kryptforge.clusterforge.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configuração do Spring MVC para CORS global.
 * 
 * Nota: Configurações específicas do proxy WebDAV foram removidas, pois o frontend
 * agora conecta diretamente ao servidor WebDAV (que tem seu próprio CORS configurado).
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

	@Value("${clusterforge.cors.allowed-origins:http://localhost:3000,http://localhost:3001,http://localhost:3002}")
	private String allowedOrigins;

	/**
	 * Configuração global de CORS para a API REST.
	 * O WebDAV agora tem seu próprio CORS configurado no servidor.
	 */
	@Override
	public void addCorsMappings(CorsRegistry registry) {
		// Parse das origens permitidas
		String[] origins = allowedOrigins.split(",");
		for (int i = 0; i < origins.length; i++) {
			origins[i] = origins[i].trim();
		}
		
		// CORS apenas para a API REST (não para WebDAV, que tem seu próprio CORS)
		registry.addMapping("/api/**")
			.allowedOrigins(origins)
			.allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH", "HEAD")
			.allowedHeaders("*")
			.allowCredentials(true)
			.maxAge(3600);
	}
}

