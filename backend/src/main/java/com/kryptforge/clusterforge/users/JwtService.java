package com.kryptforge.clusterforge.users;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import javax.crypto.SecretKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {

	private final JwtProperties jwtProperties;
	private static final Logger log = LoggerFactory.getLogger(JwtService.class);

	public JwtService(JwtProperties jwtProperties) {
		this.jwtProperties = jwtProperties;
	}

	private SecretKey getSigningKey() {
		String secretKey = jwtProperties.getSecretKey();
		if (secretKey == null || secretKey.isBlank()) {
			throw new IllegalStateException("JWT secret key não configurada");
		}
		byte[] keyBytes = secretKey.getBytes();
		// HMAC-SHA256 requer pelo menos 256 bits (32 bytes)
		// Keys.hmacShaKeyFor() ajusta automaticamente a chave se necessário
		return Keys.hmacShaKeyFor(keyBytes);
	}

	public String generateToken(User user) {
		Map<String, Object> claims = new HashMap<>();
		claims.put("userId", user.getId().toString());
		claims.put("username", user.getUsername());
		claims.put("role", user.getRole().name());
		return createToken(claims, user.getUsername());
	}

	private String createToken(Map<String, Object> claims, String subject) {
		Date now = new Date();
		Date expiryDate = new Date(now.getTime() + jwtProperties.getExpirationMs());

		return Jwts.builder()
			.claims(claims)
			.subject(subject)
			.issuedAt(now)
			.expiration(expiryDate)
			.signWith(getSigningKey())
			.compact();
	}

	public String extractUsername(String token) {
		return extractClaim(token, Claims::getSubject);
	}

	public UUID extractUserId(String token) {
		String userId = extractClaim(token, claims -> claims.get("userId", String.class));
		return userId != null ? UUID.fromString(userId) : null;
	}

	public String extractRole(String token) {
		return extractClaim(token, claims -> claims.get("role", String.class));
	}

	public Date extractExpiration(String token) {
		return extractClaim(token, Claims::getExpiration);
	}

	public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
		final Claims claims = extractAllClaims(token);
		return claimsResolver.apply(claims);
	}

	private Claims extractAllClaims(String token) {
		return Jwts.parser()
			.verifyWith(getSigningKey())
			.build()
			.parseSignedClaims(token)
			.getPayload();
	}

	public Boolean isTokenExpired(String token) {
		try {
			return extractExpiration(token).before(new Date());
		} catch (Exception e) {
			log.warn("Erro ao verificar expiração do token: {}", e.getMessage());
			return true;
		}
	}

	public Boolean validateToken(String token, String username) {
		try {
			final String tokenUsername = extractUsername(token);
			return (tokenUsername.equals(username) && !isTokenExpired(token));
		} catch (Exception e) {
			log.warn("Erro ao validar token: {}", e.getMessage());
			return false;
		}
	}

	public Boolean validateToken(String token) {
		try {
			// Tenta extrair claims (isso valida a assinatura automaticamente)
			extractAllClaims(token);
			// Verifica expiração
			return !isTokenExpired(token);
		} catch (Exception e) {
			log.warn("Erro ao validar token: {}", e.getMessage());
			return false;
		}
	}
}

