package com.kryptforge.clusterforge.web;

import org.springframework.context.annotation.Configuration;

/**
 * Configuração do Spring MVC.
 * 
 * Nota: A configuração de CORS foi movida para SecurityConfig para evitar conflitos.
 * O WebDAV tem seu próprio CORS configurado no servidor.
 */
@Configuration
public class WebMvcConfig {
	// Configuração de CORS movida para SecurityConfig
	// para garantir consistência e evitar conflitos entre Spring MVC e Spring Security
}

