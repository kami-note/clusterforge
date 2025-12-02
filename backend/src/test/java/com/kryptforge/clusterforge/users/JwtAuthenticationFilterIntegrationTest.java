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
 * Testes de integração para JwtAuthenticationFilter.
 * Testa que o filtro valida tokens JWT corretamente e permite/nega acesso.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
	"clusterforge.jwt.secret-key=testSecretKeyWithAtLeast32CharactersForHMAC",
	"clusterforge.jwt.expiration-ms=3600000",
	"spring.datasource.url=jdbc:h2:mem:testdb-filter;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
	"spring.datasource.username=sa",
	"spring.datasource.password=",
	"spring.jpa.hibernate.ddl-auto=create-drop"
})
class JwtAuthenticationFilterIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserService userService;

	@Autowired
	private JwtService jwtService;

	@Autowired
	private ObjectMapper objectMapper;

	private User testUser;
	private String validToken;

	@BeforeEach
	void setup() {
		// Cria usuário de teste com nome único para evitar conflitos
		String uniqueUsername = "testuser-" + System.currentTimeMillis();
		testUser = userService.create(uniqueUsername, "password123", Role.USER);
		// Gera token válido
		validToken = jwtService.generateToken(testUser);
	}

	@Test
	@DisplayName("requisição sem token deve retornar 401 para endpoint protegido")
	void requestWithoutToken_shouldReturn401ForProtectedEndpoint() throws Exception {
		mockMvc.perform(get("/api/clusters"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("requisição com token válido deve ter acesso a endpoint protegido")
	void requestWithValidToken_shouldAccessProtectedEndpoint() throws Exception {
		mockMvc.perform(get("/api/clusters")
			.header("Authorization", "Bearer " + validToken))
			.andExpect(status().isOk());
	}

	@Test
	@DisplayName("requisição com token inválido deve retornar 403")
	void requestWithInvalidToken_shouldReturn403() throws Exception {
		mockMvc.perform(get("/api/clusters")
			.header("Authorization", "Bearer invalid.token.here"))
			.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("requisição com token malformado deve retornar 403")
	void requestWithMalformedToken_shouldReturn403() throws Exception {
		mockMvc.perform(get("/api/clusters")
			.header("Authorization", "Bearer not.a.valid.token"))
			.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("requisição sem prefixo Bearer deve retornar 401")
	void requestWithoutBearerPrefix_shouldReturn401() throws Exception {
		mockMvc.perform(get("/api/clusters")
			.header("Authorization", validToken))
			.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("endpoint de login deve ser acessível sem token")
	void loginEndpoint_shouldBeAccessibleWithoutToken() throws Exception {
		LoginRequest request = new LoginRequest(testUser.getUsername(), "password123");

		mockMvc.perform(post("/api/auth/login")
			.contentType(MediaType.APPLICATION_JSON)
			.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isOk());
	}

	@Test
	@DisplayName("endpoint raiz deve ser acessível sem token")
	void rootEndpoint_shouldBeAccessibleWithoutToken() throws Exception {
		mockMvc.perform(get("/"))
			.andExpect(status().isOk());
	}

	@Test
	@DisplayName("token expirado deve retornar 403")
	void expiredToken_shouldReturn403() throws Exception {
		// Cria token com expiração muito curta
		JwtProperties props = new JwtProperties();
		props.setSecretKey("testSecretKeyWithAtLeast32CharactersForHMAC");
		props.setExpirationMs(1); // 1ms - expira imediatamente
		JwtService shortLivedJwtService = new JwtService(props);
		
		// Espera um pouco para garantir que expirou
		Thread.sleep(10);
		
		String expiredToken = shortLivedJwtService.generateToken(testUser);
		Thread.sleep(10); // Espera mais um pouco

		mockMvc.perform(get("/api/clusters")
			.header("Authorization", "Bearer " + expiredToken))
			.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("usuário com role USER deve ter acesso ao endpoint protegido")
	void userWithUserRole_shouldAccessProtectedEndpoint() throws Exception {
		String uniqueUsername = "user-" + System.currentTimeMillis();
		User user = userService.create(uniqueUsername, "password", Role.USER);
		String token = jwtService.generateToken(user);

		mockMvc.perform(get("/api/clusters")
			.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk());
	}

	@Test
	@DisplayName("usuário com role ADMIN deve ter acesso ao endpoint protegido")
	void userWithAdminRole_shouldAccessProtectedEndpoint() throws Exception {
		String uniqueUsername = "admin-" + System.currentTimeMillis();
		User admin = userService.create(uniqueUsername, "password", Role.ADMIN);
		String token = jwtService.generateToken(admin);

		mockMvc.perform(get("/api/clusters")
			.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk());
	}
}

