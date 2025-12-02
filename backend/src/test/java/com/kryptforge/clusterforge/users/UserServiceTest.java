package com.kryptforge.clusterforge.users;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class UserServiceTest {

	private UserRepository repository;
	private PasswordEncoder passwordEncoder;
	private UserService service;

	@BeforeEach
	void setup() {
		repository = mock(UserRepository.class);
		passwordEncoder = mock(PasswordEncoder.class);
		service = new UserService(repository, passwordEncoder);
	}

	@Test
	@DisplayName("create deve criar usuário com sucesso")
	void create_shouldCreateUserSuccessfully() {
		String username = "testuser";
		String password = "password123";
		Role role = Role.USER;

		when(repository.existsByUsername(username)).thenReturn(false);
		when(passwordEncoder.encode(password)).thenReturn("encodedPassword");
		when(repository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

		User created = service.create(username, password, role);

		assertNotNull(created);
		assertEquals(username, created.getUsername());
		assertEquals("encodedPassword", created.getPassword());
		assertEquals(role, created.getRole());
		verify(repository).save(any(User.class));
		verify(passwordEncoder).encode(password);
	}

	@Test
	@DisplayName("create deve lançar exceção se username vazio")
	void create_shouldThrowExceptionWhenUsernameEmpty() {
		assertThrows(IllegalArgumentException.class, () -> {
			service.create("", "password", Role.USER);
		});
		assertThrows(IllegalArgumentException.class, () -> {
			service.create(null, "password", Role.USER);
		});
	}

	@Test
	@DisplayName("create deve lançar exceção se password vazio")
	void create_shouldThrowExceptionWhenPasswordEmpty() {
		assertThrows(IllegalArgumentException.class, () -> {
			service.create("user", "", Role.USER);
		});
		assertThrows(IllegalArgumentException.class, () -> {
			service.create("user", null, Role.USER);
		});
	}

	@Test
	@DisplayName("create deve lançar exceção se role vazio")
	void create_shouldThrowExceptionWhenRoleNull() {
		assertThrows(IllegalArgumentException.class, () -> {
			service.create("user", "password", null);
		});
	}

	@Test
	@DisplayName("create deve lançar exceção se username já existe")
	void create_shouldThrowExceptionWhenUsernameExists() {
		String username = "existinguser";
		when(repository.existsByUsername(username)).thenReturn(true);

		assertThrows(IllegalArgumentException.class, () -> {
			service.create(username, "password", Role.USER);
		});
	}

	@Test
	@DisplayName("findByUsername deve retornar usuário quando existe")
	void findByUsername_shouldReturnUserWhenExists() {
		String username = "testuser";
		User user = new User();
		user.setUsername(username);
		user.setRole(Role.USER);

		when(repository.findByUsername(username)).thenReturn(Optional.of(user));

		Optional<User> result = service.findByUsername(username);

		assertTrue(result.isPresent());
		assertEquals(username, result.get().getUsername());
	}

	@Test
	@DisplayName("findByUsername deve retornar empty quando não existe")
	void findByUsername_shouldReturnEmptyWhenNotExists() {
		String username = "nonexistent";
		when(repository.findByUsername(username)).thenReturn(Optional.empty());

		Optional<User> result = service.findByUsername(username);

		assertTrue(result.isEmpty());
	}

	@Test
	@DisplayName("authenticate deve retornar true quando credenciais válidas")
	void authenticate_shouldReturnTrueWhenCredentialsValid() {
		String username = "testuser";
		String password = "password123";
		User user = new User();
		user.setUsername(username);
		user.setPassword("encodedPassword");

		when(repository.findByUsername(username)).thenReturn(Optional.of(user));
		when(passwordEncoder.matches(password, "encodedPassword")).thenReturn(true);

		boolean result = service.authenticate(username, password);

		assertTrue(result);
		verify(passwordEncoder).matches(password, "encodedPassword");
	}

	@Test
	@DisplayName("authenticate deve retornar false quando usuário não existe")
	void authenticate_shouldReturnFalseWhenUserNotExists() {
		String username = "nonexistent";
		when(repository.findByUsername(username)).thenReturn(Optional.empty());

		boolean result = service.authenticate(username, "password");

		assertFalse(result);
		verify(passwordEncoder, never()).matches(anyString(), anyString());
	}

	@Test
	@DisplayName("authenticate deve retornar false quando password incorreto")
	void authenticate_shouldReturnFalseWhenPasswordIncorrect() {
		String username = "testuser";
		String password = "wrongpassword";
		User user = new User();
		user.setUsername(username);
		user.setPassword("encodedPassword");

		when(repository.findByUsername(username)).thenReturn(Optional.of(user));
		when(passwordEncoder.matches(password, "encodedPassword")).thenReturn(false);

		boolean result = service.authenticate(username, password);

		assertFalse(result);
		verify(passwordEncoder).matches(password, "encodedPassword");
	}
}

