package com.kryptforge.clusterforge.users;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kryptforge.clusterforge.users.dto.AuthDtos.LoginRequest;

/**
 * Testes de integração para AuthController usando MockMvc.
 * Testa o fluxo completo de autenticação com JWT.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
	"clusterforge.jwt.secret-key=testSecretKeyWithAtLeast32CharactersForHMAC",
	"clusterforge.jwt.expiration-ms=3600000",
	"spring.datasource.url=jdbc:h2:mem:testdb-auth;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
	"spring.datasource.username=sa",
	"spring.datasource.password=",
	"spring.jpa.hibernate.ddl-auto=create-drop"
})
class AuthControllerIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserService userService;

	@Autowired
	private ObjectMapper objectMapper;

	private User testUser;

	@BeforeEach
	void setup() {
		// Cria usuário de teste com nome único para evitar conflitos
		String uniqueUsername = "testuser-" + System.currentTimeMillis();
		testUser = userService.create(uniqueUsername, "password123", Role.USER);
	}

	@Test
	@DisplayName("login deve retornar token JWT quando credenciais válidas")
	void login_shouldReturnJwtTokenWhenCredentialsValid() throws Exception {
		LoginRequest request = new LoginRequest(testUser.getUsername(), "password123");

		mockMvc.perform(post("/api/auth/login")
			.contentType(MediaType.APPLICATION_JSON)
			.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.token").exists())
			.andExpect(jsonPath("$.token").isString())
			.andExpect(jsonPath("$.username").value(testUser.getUsername()))
			.andExpect(jsonPath("$.role").value("USER"))
			.andExpect(jsonPath("$.userId").value(testUser.getId().toString()));
	}

	@Test
	@DisplayName("login deve retornar 401 quando username incorreto")
	void login_shouldReturn401WhenUsernameIncorrect() throws Exception {
		LoginRequest request = new LoginRequest("wronguser", "password123");

		mockMvc.perform(post("/api/auth/login")
			.contentType(MediaType.APPLICATION_JSON)
			.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("login deve retornar 401 quando password incorreto")
	void login_shouldReturn401WhenPasswordIncorrect() throws Exception {
		LoginRequest request = new LoginRequest(testUser.getUsername(), "wrongpassword");

		mockMvc.perform(post("/api/auth/login")
			.contentType(MediaType.APPLICATION_JSON)
			.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("login deve retornar 400 quando request inválido")
	void login_shouldReturn400WhenRequestInvalid() throws Exception {
		LoginRequest request = new LoginRequest("", "password123");

		mockMvc.perform(post("/api/auth/login")
			.contentType(MediaType.APPLICATION_JSON)
			.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("login deve funcionar com usuário ADMIN")
	void login_shouldWorkWithAdminUser() throws Exception {
		String uniqueUsername = "admin-" + System.currentTimeMillis();
		User adminUser = userService.create(uniqueUsername, "admin123", Role.ADMIN);
		LoginRequest request = new LoginRequest(uniqueUsername, "admin123");

		mockMvc.perform(post("/api/auth/login")
			.contentType(MediaType.APPLICATION_JSON)
			.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.token").exists())
			.andExpect(jsonPath("$.username").value(uniqueUsername))
			.andExpect(jsonPath("$.role").value("ADMIN"))
			.andExpect(jsonPath("$.userId").value(adminUser.getId().toString()));
	}
}

