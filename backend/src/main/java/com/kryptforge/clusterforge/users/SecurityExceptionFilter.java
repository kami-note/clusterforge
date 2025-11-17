package com.kryptforge.clusterforge.users;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Filtro para capturar exceções de segurança antes que a resposta seja commitada.
 * Este filtro deve ser posicionado antes do CORS para garantir que erros de autorização
 * sejam tratados antes que headers CORS sejam escritos na resposta.
 */
@Component
public class SecurityExceptionFilter extends OncePerRequestFilter {

	private static final Logger log = LoggerFactory.getLogger(SecurityExceptionFilter.class);
	
	private final AuthenticationEntryPoint authenticationEntryPoint;
	private final AccessDeniedHandler accessDeniedHandler;

	public SecurityExceptionFilter(
		AuthenticationEntryPoint authenticationEntryPoint,
		AccessDeniedHandler accessDeniedHandler
	) {
		this.authenticationEntryPoint = authenticationEntryPoint;
		this.accessDeniedHandler = accessDeniedHandler;
	}

	@Override
	protected void doFilterInternal(
		@NonNull HttpServletRequest request,
		@NonNull HttpServletResponse response,
		@NonNull FilterChain filterChain
	) throws ServletException, IOException {
		try {
			filterChain.doFilter(request, response);
		} catch (AuthenticationException e) {
			log.debug("AuthenticationException capturada pelo SecurityExceptionFilter para {} {}: {}", 
				request.getMethod(), request.getRequestURI(), e.getMessage());
			if (!response.isCommitted()) {
				authenticationEntryPoint.commence(request, response, e);
			}
		} catch (AccessDeniedException e) {
			// AuthorizationDeniedException é uma subclasse de AccessDeniedException
			// Captura ambas as exceções de acesso negado
			log.debug("Exceção de acesso negado capturada pelo SecurityExceptionFilter para {} {}: {}", 
				request.getMethod(), request.getRequestURI(), e.getMessage());
			if (!response.isCommitted()) {
				accessDeniedHandler.handle(request, response, e);
			}
		}
	}
}

