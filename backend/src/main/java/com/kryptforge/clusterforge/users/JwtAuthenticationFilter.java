package com.kryptforge.clusterforge.users;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.access.AccessDeniedException;
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
			log.debug("Requisição {} {} sem header Authorization ou sem prefixo Bearer", 
				request.getMethod(), request.getRequestURI());
			filterChain.doFilter(request, response);
			return;
		}

		try {
			String token = authHeader.substring(7);
			
			// Valida token (assinatura e expiração)
			if (!jwtService.validateToken(token)) {
				log.warn("Token JWT inválido ou expirado para requisição: {} {} - Token length: {}", 
					request.getMethod(), request.getRequestURI(), token.length());
				// Limpar contexto de segurança se houver autenticação inválida
				SecurityContextHolder.clearContext();
				// Lançar AccessDeniedException para retornar 403 (token inválido = tentou autenticar mas foi negado)
				throw new AccessDeniedException("Token JWT inválido ou expirado");
			}

			String username = jwtService.extractUsername(token);
			
			if (username == null) {
				log.warn("Não foi possível extrair username do token JWT");
				SecurityContextHolder.clearContext();
				// Lançar AccessDeniedException para retornar 403 (token malformado = tentou autenticar mas foi negado)
				throw new AccessDeniedException("Token JWT malformado: não foi possível extrair username");
			}
			
			// Se já existe autenticação, não sobrescrever (pode ser de outro filtro)
			if (SecurityContextHolder.getContext().getAuthentication() != null) {
				log.debug("Autenticação já existe no contexto de segurança");
				filterChain.doFilter(request, response);
				return;
			}

			User user = userService.findByUsername(username).orElse(null);
			
			if (user == null) {
				log.warn("Usuário '{}' do token JWT não encontrado no banco de dados", username);
				SecurityContextHolder.clearContext();
				// Lançar AccessDeniedException para retornar 403 (usuário não existe = tentou autenticar mas foi negado)
				throw new AccessDeniedException("Usuário do token não encontrado");
			}
			
			// Verifica se o username do token corresponde ao usuário encontrado
			if (!jwtService.validateToken(token, username)) {
				log.warn("Token JWT não corresponde ao usuário '{}'", username);
				// Limpar contexto de segurança se houver autenticação inválida
				SecurityContextHolder.clearContext();
				// Lançar AccessDeniedException para retornar 403 (token não corresponde = tentou autenticar mas foi negado)
				throw new AccessDeniedException("Token JWT não corresponde ao usuário");
			}

			// Verifica se a role no token corresponde à role do usuário
			String role = jwtService.extractRole(token);
			if (role == null || !role.equals(user.getRole().name())) {
				log.warn("Role do token '{}' não corresponde à role do usuário '{}' para usuário '{}'", 
					role, user.getRole(), username);
				SecurityContextHolder.clearContext();
				// Lançar AccessDeniedException para retornar 403 (role não corresponde = tentou autenticar mas foi negado)
				throw new AccessDeniedException("Role do token não corresponde à role do usuário");
			}

			SimpleGrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + role);
			
			UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
				user,
				null,
				java.util.Collections.singletonList(authority)
			);
			
			authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
			SecurityContextHolder.getContext().setAuthentication(authToken);
			log.info("Autenticação JWT bem-sucedida para usuário '{}' com role '{}' na requisição {} {}", 
				username, role, request.getMethod(), request.getRequestURI());
		} catch (AccessDeniedException e) {
			// Re-lançar AccessDeniedException para que o SecurityExceptionFilter possa tratá-la
			throw e;
		} catch (Exception e) {
			log.warn("Erro ao processar token JWT para requisição {} {}: {}", 
				request.getMethod(), request.getRequestURI(), e.getMessage());
			// Limpar contexto de segurança em caso de erro
			SecurityContextHolder.clearContext();
			// Lançar AccessDeniedException para retornar 403 (erro ao processar token = tentou autenticar mas foi negado)
			throw new AccessDeniedException("Erro ao processar token JWT: " + e.getMessage(), e);
		}

		// Sempre continuar a cadeia - o Spring Security tratará a autorização
		filterChain.doFilter(request, response);
	}
}

