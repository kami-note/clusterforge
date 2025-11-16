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
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
			.csrf(csrf -> csrf.disable())
			.cors(cors -> cors.configurationSource(corsConfigurationSource()))
			.authorizeHttpRequests(auth -> auth
				.requestMatchers("/api/auth/**").permitAll()
				.requestMatchers("/").permitAll()
				.requestMatchers("/h2-console/**").permitAll()
				.requestMatchers(HttpMethod.GET, "/api/templates").permitAll()
				.requestMatchers(HttpMethod.GET, "/api/templates/**").permitAll()
				.anyRequest().authenticated()
			)
			.sessionManagement(session -> session
				.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
			)
			.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
			.headers(headers -> headers.frameOptions(frameOptions -> frameOptions.sameOrigin()));

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
		
		// Métodos HTTP permitidos
		configuration.setAllowedMethods(Arrays.asList(
			HttpMethod.GET.name(),
			HttpMethod.POST.name(),
			HttpMethod.PUT.name(),
			HttpMethod.PATCH.name(),
			HttpMethod.DELETE.name(),
			HttpMethod.OPTIONS.name()
		));
		
		// Headers permitidos
		configuration.setAllowedHeaders(Arrays.asList(
			"Authorization",
			"Content-Type",
			"X-Requested-With",
			"Accept",
			"Origin",
			"Access-Control-Request-Method",
			"Access-Control-Request-Headers",
			"X-Auth-Event",
			"X-Auth-Reason"
		));
		
		// Headers expostos para o frontend
		configuration.setExposedHeaders(Arrays.asList(
			"Authorization",
			"X-Auth-Event",
			"X-Auth-Reason"
		));
		
		// Permitir credenciais (cookies, headers de autenticação)
		configuration.setAllowCredentials(true);
		
		// Tempo de cache para preflight requests
		configuration.setMaxAge(3600L);
		
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		// Aplicar CORS para todos os endpoints da API
		source.registerCorsConfiguration("/api/**", configuration);
		// Aplicar CORS também para endpoints Docker (incluindo SSE)
		source.registerCorsConfiguration("/docker/**", configuration);
		
		return source;
	}
}

