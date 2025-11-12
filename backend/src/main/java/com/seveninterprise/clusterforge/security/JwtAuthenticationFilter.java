package com.seveninterprise.clusterforge.security;

import com.seveninterprise.clusterforge.services.UserDetailsServiceImpl;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String AUTH_EVENT_HEADER = "X-Auth-Event";
    private static final String AUTH_REASON_HEADER = "X-Auth-Reason";
    private static final String EVENT_LOGOUT = "LOGOUT";
    private static final String REASON_TOKEN_EXPIRED = "TOKEN_EXPIRED";
    private static final String REASON_INVALID_TOKEN = "INVALID_TOKEN";

    private final JwtProvider jwtProvider;
    private final UserDetailsServiceImpl userDetailsService;

    public JwtAuthenticationFilter(JwtProvider jwtProvider, UserDetailsServiceImpl userDetailsService) {
        this.jwtProvider = jwtProvider;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        try {
            final String authHeader = request.getHeader("Authorization");
            final String jwt;
            final String username;

            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                filterChain.doFilter(request, response);
                return;
            }

            jwt = authHeader.substring(7);
            username = jwtProvider.extractUsername(jwt);

            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = this.userDetailsService.loadUserByUsername(username);
                if (jwtProvider.validateToken(jwt, userDetails)) {
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities());
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
            filterChain.doFilter(request, response);
        } catch (ExpiredJwtException ex) {
            LOGGER.debug("Access token expirado para request {} {}", request.getMethod(), request.getRequestURI());
            sendUnauthorized(response, REASON_TOKEN_EXPIRED, "Token JWT expirado. Efetue o login novamente.");
        } catch (JwtException | IllegalArgumentException ex) {
            LOGGER.warn("Token JWT inválido para request {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
            sendUnauthorized(response, REASON_INVALID_TOKEN, "Token JWT inválido.");
        }
    }

    private void sendUnauthorized(HttpServletResponse response, String reasonCode, String message) throws IOException {
        if (response.isCommitted()) {
            return;
        }

        response.resetBuffer();
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json");
        response.setHeader(AUTH_EVENT_HEADER, EVENT_LOGOUT);
        response.setHeader(AUTH_REASON_HEADER, reasonCode);

        String payload = String.format("{\"message\":\"%s\",\"code\":\"%s\"}", message, reasonCode);
        try (PrintWriter writer = response.getWriter()) {
            writer.write(payload);
            writer.flush();
        }
    }
}
