package com.kryptforge.clusterforge.users;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

	private final JwtAuthenticationFilter jwtAuthenticationFilter;
	
	@Value("${clusterforge.cors.allowed-origins:http://localhost:3000,http://localhost:3001,http://localhost:3002}")
	private String allowedOrigins;

	public SecurityConfig(@Lazy JwtAuthenticationFilter jwtAuthenticationFilter) {
		this.jwtAuthenticationFilter = jwtAuthenticationFilter;
	}

	@Bean
	public org.springframework.security.web.AuthenticationEntryPoint authenticationEntryPoint() {
		return (request, response, authException) -> {
			// Não fazer nada se a resposta já foi commitada (evita erro duplo)
			if (response.isCommitted()) {
				return;
			}
			
			// Nota: Headers CORS para WebDAV não são mais necessários aqui,
			// pois o frontend conecta diretamente ao servidor WebDAV
			
			response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
			response.setContentType("application/json");
			response.setCharacterEncoding("UTF-8");
			try {
				response.getWriter().write("{\"error\":\"Não autenticado\",\"message\":\"Token inválido ou ausente\"}");
			} catch (Exception e) {
				// Ignorar se não conseguir escrever (resposta pode ter sido commitada durante a escrita)
			}
		};
	}

	@Bean
	public org.springframework.security.web.access.AccessDeniedHandler accessDeniedHandler() {
		return (request, response, accessDeniedException) -> {
			// Não fazer nada se a resposta já foi commitada (evita erro duplo)
			// Isso é comum em requisições assíncronas/streaming onde a resposta já começou a ser enviada
			if (response.isCommitted()) {
				// Silenciosamente ignorar - não logar como erro pois é comportamento esperado
				// em requisições assíncronas onde a autorização é verificada após o início do stream
				return;
			}
			
			// Nota: Headers CORS para WebDAV não são mais necessários aqui,
			// pois o frontend conecta diretamente ao servidor WebDAV
			
			response.setStatus(HttpServletResponse.SC_FORBIDDEN);
			response.setContentType("application/json");
			response.setCharacterEncoding("UTF-8");
			try {
				response.getWriter().write("{\"error\":\"Acesso negado\",\"message\":\"Você não tem permissão para acessar este recurso\"}");
			} catch (Exception e) {
				// Ignorar silenciosamente se não conseguir escrever (resposta pode ter sido commitada durante a escrita)
				// Isso é comum em requisições assíncronas
			}
		};
	}

	@Bean
	public SecurityFilterChain securityFilterChain(
		HttpSecurity http,
		SecurityExceptionFilter securityExceptionFilter,
		org.springframework.security.web.AuthenticationEntryPoint authenticationEntryPoint,
		org.springframework.security.web.access.AccessDeniedHandler accessDeniedHandler
	) throws Exception {
		http
			.csrf(csrf -> csrf.disable())
			.cors(cors -> cors.configurationSource(corsConfigurationSource()))
			.authorizeHttpRequests(auth -> auth
				// IMPORTANTE: Permitir requisições de dispatch assíncrono sem re-autorização
				// Isso evita AuthorizationDeniedException em SSE/streaming quando a resposta já foi commitada
				// A autorização já foi feita na requisição original, não precisa re-verificar no dispatch assíncrono
				.dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()
				// Permitir requisições de error dispatch (para tratamento de erros)
				.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
				// Permitir OPTIONS requests (preflight CORS) sem autenticação
				.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
				.requestMatchers("/api/auth/**").permitAll()
				.requestMatchers("/").permitAll()
				.requestMatchers("/h2-console/**").permitAll()
				.requestMatchers(HttpMethod.GET, "/api/templates").permitAll()
				.requestMatchers(HttpMethod.GET, "/api/templates/**").permitAll()
				// Nota: Requisições WebDAV não passam mais pelo backend,
				// o frontend conecta diretamente ao servidor WebDAV
				.anyRequest().authenticated()
			)
			.sessionManagement(session -> session
				.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
			)
			// Adicionar filtro de exceções antes do CORS para capturar exceções antes que a resposta seja commitada
			.addFilterBefore(securityExceptionFilter, org.springframework.web.filter.CorsFilter.class)
			.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
			.headers(headers -> headers.frameOptions(frameOptions -> frameOptions.sameOrigin()))
			// Configurar tratamento de exceções para evitar logs de erro quando a resposta já foi enviada
			.exceptionHandling(exceptions -> exceptions
				.authenticationEntryPoint(authenticationEntryPoint)
				.accessDeniedHandler(accessDeniedHandler)
			);

		return http.build();
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
		return config.getAuthenticationManager();
	}

	/**
	 * Configuração de CORS para permitir requisições do frontend
	 * As origens permitidas são configuradas via application.properties
	 * propriedade: clusterforge.cors.allowed-origins
	 * Formato: URLs separadas por vírgula (ex: http://localhost:3000,http://localhost:3001)
	 */
	@Bean
	public CorsConfigurationSource corsConfigurationSource() {
		CorsConfiguration configuration = new CorsConfiguration();
		
		// Lê as origens permitidas do application.properties
		// Remove espaços em branco e filtra strings vazias
		List<String> origins = Arrays.stream(allowedOrigins.split(","))
			.map(String::trim)
			.filter(origin -> !origin.isEmpty())
			.collect(Collectors.toList());
		
		if (origins.isEmpty()) {
			// Fallback para valores padrão se a propriedade estiver vazia
			origins = Arrays.asList(
				"http://localhost:3000",
				"http://localhost:3001",
				"http://localhost:3002"
			);
		}
		
		configuration.setAllowedOrigins(origins);
		
		// Métodos HTTP permitidos para a API REST
		// Nota: Métodos WebDAV (PROPFIND, MKCOL, etc.) não são mais necessários aqui,
		// pois o frontend conecta diretamente ao servidor WebDAV (que tem seu próprio CORS)
		configuration.setAllowedMethods(Arrays.asList(
			HttpMethod.GET.name(),
			HttpMethod.POST.name(),
			HttpMethod.PUT.name(),
			HttpMethod.PATCH.name(),
			HttpMethod.DELETE.name(),
			HttpMethod.OPTIONS.name(),
			HttpMethod.HEAD.name()
		));
		
		// Headers permitidos para a API REST e SSE
		configuration.setAllowedHeaders(Arrays.asList(
			"Authorization",
			"Content-Type",
			"X-Requested-With",
			"Accept",
			"Origin",
			"Access-Control-Request-Method",
			"Access-Control-Request-Headers",
			"X-Auth-Event",
			"X-Auth-Reason",
			"Cache-Control",
			"Last-Event-ID"
		));
		
		// Headers expostos para o frontend (incluindo headers necessários para SSE)
		configuration.setExposedHeaders(Arrays.asList(
			"Authorization",
			"X-Auth-Event",
			"X-Auth-Reason",
			"Content-Type",
			"Cache-Control",
			"Last-Event-ID"
		));
		
		// Permitir credenciais (cookies, headers de autenticação)
		configuration.setAllowCredentials(true);
		
		// Tempo de cache para preflight requests
		configuration.setMaxAge(3600L);
		
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		// Aplicar CORS para todos os endpoints da API (incluindo SSE)
		// A configuração já inclui todos os headers necessários para SSE
		source.registerCorsConfiguration("/api/**", configuration);
		// Aplicar CORS também para endpoints Docker (caso sejam acessados diretamente)
		source.registerCorsConfiguration("/docker/**", configuration);
		
		return source;
	}
}

