package com.kryptforge.clusterforge.users;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Date;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

	private JwtProperties jwtProperties;
	private JwtService jwtService;

	@BeforeEach
	void setup() {
		jwtProperties = new JwtProperties();
		jwtProperties.setSecretKey("testSecretKeyWithAtLeast32CharactersForHMAC");
		jwtProperties.setExpirationMs(3600000); // 1 hora
		jwtService = new JwtService(jwtProperties);
	}

	@Test
	@DisplayName("generateToken deve gerar token válido")
	void generateToken_shouldGenerateValidToken() {
		User user = createTestUser();

		String token = jwtService.generateToken(user);

		assertNotNull(token);
		assertFalse(token.isEmpty());
	}

	@Test
	@DisplayName("extractUsername deve extrair username do token")
	void extractUsername_shouldExtractUsernameFromToken() {
		User user = createTestUser();
		String token = jwtService.generateToken(user);

		String username = jwtService.extractUsername(token);

		assertEquals(user.getUsername(), username);
	}

	@Test
	@DisplayName("extractUserId deve extrair userId do token")
	void extractUserId_shouldExtractUserIdFromToken() {
		User user = createTestUser();
		String token = jwtService.generateToken(user);

		UUID userId = jwtService.extractUserId(token);

		assertEquals(user.getId(), userId);
	}

	@Test
	@DisplayName("extractRole deve extrair role do token")
	void extractRole_shouldExtractRoleFromToken() {
		User user = createTestUser();
		user.setRole(Role.ADMIN);
		String token = jwtService.generateToken(user);

		String role = jwtService.extractRole(token);

		assertEquals(Role.ADMIN.name(), role);
	}

	@Test
	@DisplayName("validateToken deve retornar true para token válido")
	void validateToken_shouldReturnTrueForValidToken() {
		User user = createTestUser();
		String token = jwtService.generateToken(user);

		Boolean isValid = jwtService.validateToken(token);

		assertTrue(isValid);
	}

	@Test
	@DisplayName("validateToken deve retornar true para token válido com username")
	void validateToken_shouldReturnTrueForValidTokenWithUsername() {
		User user = createTestUser();
		String token = jwtService.generateToken(user);

		Boolean isValid = jwtService.validateToken(token, user.getUsername());

		assertTrue(isValid);
	}

	@Test
	@DisplayName("validateToken deve retornar false para username incorreto")
	void validateToken_shouldReturnFalseForIncorrectUsername() {
		User user = createTestUser();
		String token = jwtService.generateToken(user);

		Boolean isValid = jwtService.validateToken(token, "differentUsername");

		assertFalse(isValid);
	}

	@Test
	@DisplayName("isTokenExpired deve retornar false para token não expirado")
	void isTokenExpired_shouldReturnFalseForNonExpiredToken() {
		User user = createTestUser();
		String token = jwtService.generateToken(user);

		Boolean isExpired = jwtService.isTokenExpired(token);

		assertFalse(isExpired);
	}

	@Test
	@DisplayName("extractExpiration deve retornar data futura")
	void extractExpiration_shouldReturnFutureDate() {
		User user = createTestUser();
		String token = jwtService.generateToken(user);

		Date expiration = jwtService.extractExpiration(token);

		assertNotNull(expiration);
		assertTrue(expiration.after(new Date()));
	}

	private User createTestUser() {
		User user = new User();
		try {
			// Usa reflection para setar o ID para testes
			java.lang.reflect.Field idField = User.class.getDeclaredField("id");
			idField.setAccessible(true);
			idField.set(user, UUID.randomUUID());
			idField.setAccessible(false);
		} catch (Exception e) {
			throw new RuntimeException("Erro ao setar ID do usuário para teste", e);
		}
		user.setUsername("testuser");
		user.setPassword("encodedPassword");
		user.setRole(Role.USER);
		return user;
	}
}

