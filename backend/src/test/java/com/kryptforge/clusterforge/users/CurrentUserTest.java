package com.kryptforge.clusterforge.users;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

class CurrentUserTest {

	private CurrentUser currentUser;
	private SecurityContext securityContext;
	private Authentication authentication;

	@BeforeEach
	void setup() {
		currentUser = new CurrentUser();
		securityContext = mock(SecurityContext.class);
		authentication = mock(Authentication.class);
		SecurityContextHolder.setContext(securityContext);
	}

	@Test
	@DisplayName("getCurrentUser deve retornar usuário quando autenticado")
	void getCurrentUser_shouldReturnUserWhenAuthenticated() {
		User user = createTestUser();
		when(securityContext.getAuthentication()).thenReturn(authentication);
		when(authentication.getPrincipal()).thenReturn(user);

		Optional<User> result = currentUser.getCurrentUser();

		assertTrue(result.isPresent());
		assertEquals(user, result.get());
	}

	@Test
	@DisplayName("getCurrentUser deve retornar empty quando não autenticado")
	void getCurrentUser_shouldReturnEmptyWhenNotAuthenticated() {
		when(securityContext.getAuthentication()).thenReturn(null);

		Optional<User> result = currentUser.getCurrentUser();

		assertTrue(result.isEmpty());
	}

	@Test
	@DisplayName("getCurrentUser deve retornar empty quando principal não é User")
	void getCurrentUser_shouldReturnEmptyWhenPrincipalIsNotUser() {
		when(securityContext.getAuthentication()).thenReturn(authentication);
		when(authentication.getPrincipal()).thenReturn("stringPrincipal");

		Optional<User> result = currentUser.getCurrentUser();

		assertTrue(result.isEmpty());
	}

	@Test
	@DisplayName("getCurrentUserId deve retornar userId quando autenticado")
	void getCurrentUserId_shouldReturnUserIdWhenAuthenticated() {
		User user = createTestUser();
		UUID userId = user.getId();
		when(securityContext.getAuthentication()).thenReturn(authentication);
		when(authentication.getPrincipal()).thenReturn(user);

		Optional<UUID> result = currentUser.getCurrentUserId();

		assertTrue(result.isPresent());
		assertEquals(userId, result.get());
	}

	@Test
	@DisplayName("getCurrentUserId deve retornar empty quando não autenticado")
	void getCurrentUserId_shouldReturnEmptyWhenNotAuthenticated() {
		when(securityContext.getAuthentication()).thenReturn(null);

		Optional<UUID> result = currentUser.getCurrentUserId();

		assertTrue(result.isEmpty());
	}

	@Test
	@DisplayName("isAdmin deve retornar true quando usuário é ADMIN")
	void isAdmin_shouldReturnTrueWhenUserIsAdmin() {
		User user = createTestUser();
		user.setRole(Role.ADMIN);
		when(securityContext.getAuthentication()).thenReturn(authentication);
		when(authentication.getPrincipal()).thenReturn(user);

		boolean result = currentUser.isAdmin();

		assertTrue(result);
	}

	@Test
	@DisplayName("isAdmin deve retornar false quando usuário é USER")
	void isAdmin_shouldReturnFalseWhenUserIsUser() {
		User user = createTestUser();
		user.setRole(Role.USER);
		when(securityContext.getAuthentication()).thenReturn(authentication);
		when(authentication.getPrincipal()).thenReturn(user);

		boolean result = currentUser.isAdmin();

		assertFalse(result);
	}

	@Test
	@DisplayName("isAdmin deve retornar false quando não autenticado")
	void isAdmin_shouldReturnFalseWhenNotAuthenticated() {
		when(securityContext.getAuthentication()).thenReturn(null);

		boolean result = currentUser.isAdmin();

		assertFalse(result);
	}

	@Test
	@DisplayName("isUser deve retornar true quando usuário é USER")
	void isUser_shouldReturnTrueWhenUserIsUser() {
		User user = createTestUser();
		user.setRole(Role.USER);
		when(securityContext.getAuthentication()).thenReturn(authentication);
		when(authentication.getPrincipal()).thenReturn(user);

		boolean result = currentUser.isUser();

		assertTrue(result);
	}

	@Test
	@DisplayName("isUser deve retornar false quando usuário é ADMIN")
	void isUser_shouldReturnFalseWhenUserIsAdmin() {
		User user = createTestUser();
		user.setRole(Role.ADMIN);
		when(securityContext.getAuthentication()).thenReturn(authentication);
		when(authentication.getPrincipal()).thenReturn(user);

		boolean result = currentUser.isUser();

		assertFalse(result);
	}

	@Test
	@DisplayName("isUser deve retornar false quando não autenticado")
	void isUser_shouldReturnFalseWhenNotAuthenticated() {
		when(securityContext.getAuthentication()).thenReturn(null);

		boolean result = currentUser.isUser();

		assertFalse(result);
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

