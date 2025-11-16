package com.kryptforge.clusterforge.users;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private final JwtService jwtService;
	private final UserService userService;
	private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

	public JwtAuthenticationFilter(JwtService jwtService, UserService userService) {
		this.jwtService = jwtService;
		this.userService = userService;
	}

	@Override
	protected void doFilterInternal(
		@NonNull HttpServletRequest request,
		@NonNull HttpServletResponse response,
		@NonNull FilterChain filterChain
	) throws ServletException, IOException {
		
		String authHeader = request.getHeader("Authorization");
		
		if (authHeader == null || !authHeader.startsWith("Bearer ")) {
			filterChain.doFilter(request, response);
			return;
		}

		try {
			String token = authHeader.substring(7);
			
			// Valida token (assinatura e expiração)
			if (!jwtService.validateToken(token)) {
				filterChain.doFilter(request, response);
				return;
			}

			String username = jwtService.extractUsername(token);
			
			if (username == null || SecurityContextHolder.getContext().getAuthentication() != null) {
				filterChain.doFilter(request, response);
				return;
			}

			User user = userService.findByUsername(username).orElse(null);
			
			if (user == null) {
				log.warn("Usuário '{}' do token JWT não encontrado", username);
				filterChain.doFilter(request, response);
				return;
			}
			
			// Verifica se o username do token corresponde ao usuário encontrado
			if (!jwtService.validateToken(token, username)) {
				filterChain.doFilter(request, response);
				return;
			}

			// Verifica se a role no token corresponde à role do usuário
			String role = jwtService.extractRole(token);
			if (role == null || !role.equals(user.getRole().name())) {
				log.warn("Role do token '{}' não corresponde à role do usuário '{}'", role, user.getRole());
				filterChain.doFilter(request, response);
				return;
			}

			SimpleGrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + role);
			
			UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
				user,
				null,
				java.util.Collections.singletonList(authority)
			);
			
			authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
			SecurityContextHolder.getContext().setAuthentication(authToken);
		} catch (Exception e) {
			log.warn("Erro ao processar token JWT: {}", e.getMessage());
		}

		filterChain.doFilter(request, response);
	}
}

