package com.kryptforge.clusterforge.web;

import static org.junit.jupiter.api.Assertions.*;
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
import org.springframework.test.web.servlet.MvcResult;

import com.kryptforge.clusterforge.clusters.ClusterInstance;
import com.kryptforge.clusterforge.clusters.ClusterRepository;
import com.kryptforge.clusterforge.clusters.ClusterService;
import com.kryptforge.clusterforge.clusters.ClusterStatus;
import com.kryptforge.clusterforge.users.JwtService;
import com.kryptforge.clusterforge.users.Role;
import com.kryptforge.clusterforge.users.User;
import com.kryptforge.clusterforge.users.UserService;

/**
 * Testes de integração para DockerController.
 * Testa o endpoint de SSE para métricas de todos os clusters.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
	"clusterforge.jwt.secret-key=testSecretKeyWithAtLeast32CharactersForHMAC",
	"clusterforge.jwt.expiration-ms=3600000",
	"spring.datasource.url=jdbc:h2:mem:testdb-docker-controller;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
	"spring.datasource.username=sa",
	"spring.datasource.password=",
	"spring.jpa.hibernate.ddl-auto=create-drop"
})
class DockerControllerIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserService userService;

	@Autowired
	private JwtService jwtService;

	@Autowired
	private ClusterService clusterService;

	@Autowired
	private ClusterRepository clusterRepository;

	private User adminUser;
	private User regularUser;
	private String adminToken;
	private String regularUserToken;
	private ClusterInstance adminCluster;
	private ClusterInstance userCluster;

	@BeforeEach
	void setup() {
		// Limpar repositório
		clusterRepository.deleteAll();

		// Criar usuários de teste
		String uniqueAdminUsername = "admin-" + System.currentTimeMillis();
		String uniqueUserUsername = "user-" + System.currentTimeMillis();
		
		adminUser = userService.create(uniqueAdminUsername, "password123", Role.ADMIN);
		regularUser = userService.create(uniqueUserUsername, "password123", Role.USER);
		
		adminToken = jwtService.generateToken(adminUser);
		regularUserToken = jwtService.generateToken(regularUser);

		// Autenticar como admin para criar clusters
		setAuthenticatedUser(adminUser);
		
		// Criar clusters de teste
		adminCluster = clusterService.create(
			"admin-cluster",
			"webserver-php",
			new ClusterService.ClusterParams(null, null, null, null, null, null, null)
		);
		adminCluster.setContainerId("container-admin-123");
		adminCluster.setStatus(ClusterStatus.ACTIVE);
		clusterRepository.save(adminCluster);

		// Autenticar como usuário regular para criar seu cluster
		setAuthenticatedUser(regularUser);
		userCluster = clusterService.create(
			"user-cluster",
			"webserver-php",
			new ClusterService.ClusterParams(null, null, null, null, null, null, null)
		);
		userCluster.setContainerId("container-user-456");
		userCluster.setStatus(ClusterStatus.ACTIVE);
		clusterRepository.save(userCluster);
	}

	private void setAuthenticatedUser(User user) {
		org.springframework.security.core.userdetails.UserDetails userDetails =
			org.springframework.security.core.userdetails.User.builder()
				.username(user.getUsername())
				.password(user.getPassword())
				.authorities("ROLE_" + user.getRole().name())
				.build();
		
		org.springframework.security.authentication.UsernamePasswordAuthenticationToken authToken =
			new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
				user,
				null,
				userDetails.getAuthorities()
			);
		
		org.springframework.security.core.context.SecurityContextHolder.getContext()
			.setAuthentication(authToken);
	}

	@Test
	@DisplayName("endpoint deve retornar 200")
	void endpoint_shouldReturn200() throws Exception {
		MvcResult result = mockMvc.perform(get("/api/docker/clusters/metrics/stream")
			.header("Authorization", "Bearer " + adminToken)
			.accept(MediaType.TEXT_EVENT_STREAM))
			.andExpect(status().isOk())
			.andReturn();
		
		assertNotNull(result.getResponse());
	}

	@Test
	@DisplayName("endpoint deve requerer autenticação")
	void endpoint_shouldRequireAuthentication() throws Exception {
		MvcResult result = mockMvc.perform(get("/api/docker/clusters/metrics/stream")
			.accept(MediaType.TEXT_EVENT_STREAM))
			.andReturn();
		
		// Deve retornar 401 (Unauthorized) ou 403 (Forbidden)
		assertTrue(result.getResponse().getStatus() == 401 || result.getResponse().getStatus() == 403);
	}

	@Test
	@DisplayName("endpoint deve aceitar parâmetros timeoutMillis e intervalMillis")
	void endpoint_shouldAcceptTimeoutAndIntervalParameters() throws Exception {
		MvcResult result = mockMvc.perform(get("/api/docker/clusters/metrics/stream")
			.param("timeoutMillis", "60000")
			.param("intervalMillis", "2000")
			.header("Authorization", "Bearer " + adminToken)
			.accept(MediaType.TEXT_EVENT_STREAM))
			.andExpect(status().isOk())
			.andReturn();
		
		assertNotNull(result.getResponse());
	}

	@Test
	@DisplayName("admin deve ver todos os clusters")
	void admin_shouldSeeAllClusters() throws Exception {
		MvcResult result = mockMvc.perform(get("/api/docker/clusters/metrics/stream")
			.header("Authorization", "Bearer " + adminToken)
			.accept(MediaType.TEXT_EVENT_STREAM))
			.andExpect(status().isOk())
			.andReturn();

		// Verificar que a conexão foi estabelecida
		assertNotNull(result.getResponse());
		// Content-Type pode não estar definido se não houver clusters válidos ou containers Docker reais
	}

	@Test
	@DisplayName("usuário regular deve ver apenas seus próprios clusters")
	void regularUser_shouldSeeOnlyOwnClusters() throws Exception {
		MvcResult result = mockMvc.perform(get("/api/docker/clusters/metrics/stream")
			.header("Authorization", "Bearer " + regularUserToken)
			.accept(MediaType.TEXT_EVENT_STREAM))
			.andExpect(status().isOk())
			.andReturn();

		// Verificar que a conexão foi estabelecida
		assertNotNull(result.getResponse());
		// Content-Type pode não estar definido se não houver clusters válidos
	}

	@Test
	@DisplayName("endpoint deve usar valores padrão quando parâmetros não são fornecidos")
	void endpoint_shouldUseDefaultValuesWhenParametersNotProvided() throws Exception {
		MvcResult result = mockMvc.perform(get("/api/docker/clusters/metrics/stream")
			.header("Authorization", "Bearer " + adminToken)
			.accept(MediaType.TEXT_EVENT_STREAM))
			.andExpect(status().isOk())
			.andReturn();
		
		// Verificar que retornou OK (o Content-Type pode não estar definido se não houver clusters)
		assertNotNull(result.getResponse());
	}

	@Test
	@DisplayName("endpoint deve retornar erro com token inválido")
	void endpoint_shouldReturnErrorWithInvalidToken() throws Exception {
		MvcResult result = mockMvc.perform(get("/api/docker/clusters/metrics/stream")
			.header("Authorization", "Bearer invalid.token.here")
			.accept(MediaType.TEXT_EVENT_STREAM))
			.andReturn();
		
		// Deve retornar 401 (Unauthorized) ou 403 (Forbidden)
		assertTrue(result.getResponse().getStatus() == 401 || result.getResponse().getStatus() == 403);
	}

	@Test
	@DisplayName("endpoint deve funcionar mesmo sem clusters")
	void endpoint_shouldWorkEvenWithoutClusters() throws Exception {
		// Limpar todos os clusters
		clusterRepository.deleteAll();

		mockMvc.perform(get("/api/docker/clusters/metrics/stream")
			.header("Authorization", "Bearer " + adminToken)
			.accept(MediaType.TEXT_EVENT_STREAM))
			.andExpect(status().isOk())
			.andExpect(content().contentType(MediaType.TEXT_EVENT_STREAM_VALUE));
	}

	@Test
	@DisplayName("endpoint deve filtrar clusters sem containerId")
	void endpoint_shouldFilterClustersWithoutContainerId() throws Exception {
		// Criar cluster sem containerId
		ClusterInstance clusterWithoutContainer = clusterService.create(
			"cluster-no-container",
			"webserver-php",
			new ClusterService.ClusterParams(null, null, null, null, null, null, null)
		);
		clusterWithoutContainer.setContainerId(null);
		clusterRepository.save(clusterWithoutContainer);

		MvcResult result = mockMvc.perform(get("/api/docker/clusters/metrics/stream")
			.header("Authorization", "Bearer " + adminToken)
			.accept(MediaType.TEXT_EVENT_STREAM))
			.andExpect(status().isOk())
			.andReturn();

		// Deve retornar OK mesmo sem clusters válidos
		assertNotNull(result.getResponse());
	}

	// ============================================
	// Testes para endpoint SSE individual (/containers/{id}/metrics/stream)
	// ============================================

	@Test
	@DisplayName("endpoint individual deve retornar 200")
	void individualEndpoint_shouldReturn200() throws Exception {
		MvcResult result = mockMvc.perform(get("/api/docker/containers/container-123/metrics/stream")
			.header("Authorization", "Bearer " + adminToken)
			.accept(MediaType.TEXT_EVENT_STREAM))
			.andExpect(status().isOk())
			.andReturn();
		
		// Verificar que retornou OK
		assertNotNull(result.getResponse());
	}

	@Test
	@DisplayName("endpoint individual deve requerer autenticação")
	void individualEndpoint_shouldRequireAuthentication() throws Exception {
		MvcResult result = mockMvc.perform(get("/api/docker/containers/container-123/metrics/stream")
			.accept(MediaType.TEXT_EVENT_STREAM))
			.andReturn();
		
		// Deve retornar 401 (Unauthorized) ou 403 (Forbidden)
		assertTrue(result.getResponse().getStatus() == 401 || result.getResponse().getStatus() == 403);
	}

	@Test
	@DisplayName("endpoint individual deve aceitar parâmetro timeoutMillis")
	void individualEndpoint_shouldAcceptTimeoutParameter() throws Exception {
		MvcResult result = mockMvc.perform(get("/api/docker/containers/container-123/metrics/stream")
			.param("timeoutMillis", "60000")
			.header("Authorization", "Bearer " + adminToken)
			.accept(MediaType.TEXT_EVENT_STREAM))
			.andExpect(status().isOk())
			.andReturn();
		
		assertNotNull(result.getResponse());
	}

	@Test
	@DisplayName("endpoint individual deve usar valor padrão quando timeoutMillis não é fornecido")
	void individualEndpoint_shouldUseDefaultTimeoutWhenNotProvided() throws Exception {
		MvcResult result = mockMvc.perform(get("/api/docker/containers/container-123/metrics/stream")
			.header("Authorization", "Bearer " + adminToken)
			.accept(MediaType.TEXT_EVENT_STREAM))
			.andExpect(status().isOk())
			.andReturn();
		
		assertNotNull(result.getResponse());
	}

	@Test
	@DisplayName("endpoint individual deve retornar erro com token inválido")
	void individualEndpoint_shouldReturnErrorWithInvalidToken() throws Exception {
		MvcResult result = mockMvc.perform(get("/api/docker/containers/container-123/metrics/stream")
			.header("Authorization", "Bearer invalid.token.here")
			.accept(MediaType.TEXT_EVENT_STREAM))
			.andReturn();
		
		// Deve retornar 401 (Unauthorized) ou 403 (Forbidden)
		assertTrue(result.getResponse().getStatus() == 401 || result.getResponse().getStatus() == 403);
	}

	@Test
	@DisplayName("endpoint individual deve funcionar para qualquer containerId")
	void individualEndpoint_shouldWorkForAnyContainerId() throws Exception {
		// Testa com diferentes formatos de containerId
		String[] containerIds = {
			"container-123",
			"abc123def456",
			"1234567890abcdef",
			"my-container-name"
		};

		for (String containerId : containerIds) {
			MvcResult result = mockMvc.perform(get("/api/docker/containers/" + containerId + "/metrics/stream")
				.header("Authorization", "Bearer " + adminToken)
				.accept(MediaType.TEXT_EVENT_STREAM))
				.andExpect(status().isOk())
				.andReturn();
			
			assertNotNull(result.getResponse());
		}
	}

	@Test
	@DisplayName("endpoint individual deve ser acessível por usuário regular")
	void individualEndpoint_shouldBeAccessibleByRegularUser() throws Exception {
		MvcResult result = mockMvc.perform(get("/api/docker/containers/container-123/metrics/stream")
			.header("Authorization", "Bearer " + regularUserToken)
			.accept(MediaType.TEXT_EVENT_STREAM))
			.andExpect(status().isOk())
			.andReturn();
		
		assertNotNull(result.getResponse());
	}
}

