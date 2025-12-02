package com.kryptforge.clusterforge.users;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import com.kryptforge.clusterforge.clusters.ClusterInstance;
import com.kryptforge.clusterforge.clusters.ClusterService;
import com.kryptforge.clusterforge.clusters.ClusterStatus;
import com.kryptforge.clusterforge.templates.TemplateProperties;

/**
 * Testes de integração para ClusterService com controle de ownership.
 * Testa que usuários só podem acessar seus próprios clusters, enquanto
 * admins têm acesso a todos.
 */
@SpringBootTest
@TestPropertySource(properties = {
	"clusterforge.jwt.secret-key=testSecretKeyWithAtLeast32CharactersForHMAC",
	"clusterforge.jwt.expiration-ms=3600000",
	"spring.datasource.url=jdbc:h2:mem:testdb-ownership;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
	"spring.datasource.username=sa",
	"spring.datasource.password=",
	"spring.jpa.hibernate.ddl-auto=create-drop",
	"docker.templates.path=${java.io.tmpdir}/clusterforge-test-ownership",
	"docker.volumes.basePath=${java.io.tmpdir}/clusterforge-test-volumes-ownership"
})
class ClusterServiceWithOwnershipIntegrationTest {

	@Autowired
	private ClusterService clusterService;

	@Autowired
	private UserService userService;

	@Autowired
	private TemplateProperties templateProperties;

	private User adminUser;
	private User regularUser1;
	private User regularUser2;
	private ClusterInstance adminCluster;
	private ClusterInstance user1Cluster;
	private ClusterInstance user2Cluster;

	@BeforeEach
	void setup() throws Exception {
		// Cria template de teste
		Path templatesRoot = Path.of(templateProperties.getTemplatesPath()).toAbsolutePath();
		Files.createDirectories(templatesRoot);
		Path templateDir = templatesRoot.resolve("webserver-php");
		Files.createDirectories(templateDir);
		Files.writeString(templateDir.resolve("docker-compose.yml"),
			"version: '3.9'\n" +
			"services:\n" +
			"  app:\n" +
			"    image: alpine:latest\n" +
			"    command: [\"sh\", \"-c\", \"sleep 30\"]\n");

		// Cria usuários de teste com nomes únicos para evitar conflitos
		long timestamp = System.currentTimeMillis();
		adminUser = userService.create("admin-" + timestamp, "admin123", Role.ADMIN);
		regularUser1 = userService.create("user1-" + timestamp, "user123", Role.USER);
		regularUser2 = userService.create("user2-" + timestamp, "user123", Role.USER);

		// Simula autenticação do admin para criar clusters com nomes únicos
		setAuthenticatedUser(adminUser);
		adminCluster = clusterService.create("admin-cluster-" + timestamp, "webserver-php", null);
		
		setAuthenticatedUser(regularUser1);
		user1Cluster = clusterService.create("user1-cluster-" + timestamp, "webserver-php", null);
		
		setAuthenticatedUser(regularUser2);
		user2Cluster = clusterService.create("user2-cluster-" + timestamp, "webserver-php", null);
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
	@DisplayName("admin deve ver todos os clusters na listagem")
	void admin_shouldSeeAllClustersInList() {
		setAuthenticatedUser(adminUser);
		
		List<ClusterInstance> clusters = clusterService.list();
		
		assertTrue(clusters.size() >= 3);
		assertTrue(clusters.stream().anyMatch(c -> c.getId().equals(adminCluster.getId())));
		assertTrue(clusters.stream().anyMatch(c -> c.getId().equals(user1Cluster.getId())));
		assertTrue(clusters.stream().anyMatch(c -> c.getId().equals(user2Cluster.getId())));
	}

	@Test
	@DisplayName("user deve ver apenas seus próprios clusters na listagem")
	void user_shouldSeeOnlyOwnClustersInList() {
		setAuthenticatedUser(regularUser1);
		
		List<ClusterInstance> clusters = clusterService.list();
		
		assertEquals(1, clusters.size());
		assertEquals(user1Cluster.getId(), clusters.get(0).getId());
		assertNotEquals(adminCluster.getId(), clusters.get(0).getId());
		assertNotEquals(user2Cluster.getId(), clusters.get(0).getId());
	}

	@Test
	@DisplayName("admin deve poder acessar qualquer cluster")
	void admin_shouldAccessAnyCluster() {
		setAuthenticatedUser(adminUser);
		
		Optional<ClusterInstance> adminClusterOpt = clusterService.get(adminCluster.getId());
		Optional<ClusterInstance> user1ClusterOpt = clusterService.get(user1Cluster.getId());
		Optional<ClusterInstance> user2ClusterOpt = clusterService.get(user2Cluster.getId());
		
		assertTrue(adminClusterOpt.isPresent());
		assertTrue(user1ClusterOpt.isPresent());
		assertTrue(user2ClusterOpt.isPresent());
	}

	@Test
	@DisplayName("user deve poder acessar apenas seu próprio cluster")
	void user_shouldAccessOnlyOwnCluster() {
		setAuthenticatedUser(regularUser1);
		
		Optional<ClusterInstance> ownCluster = clusterService.get(user1Cluster.getId());
		Optional<ClusterInstance> otherUserCluster = clusterService.get(user2Cluster.getId());
		Optional<ClusterInstance> adminClusterOpt = clusterService.get(adminCluster.getId());
		
		assertTrue(ownCluster.isPresent());
		assertEquals(user1Cluster.getId(), ownCluster.get().getId());
		
		// Não deve ter acesso aos clusters de outros usuários
		assertFalse(otherUserCluster.isPresent());
		assertFalse(adminClusterOpt.isPresent());
	}

	@Test
	@DisplayName("user não deve poder atualizar cluster de outro usuário")
	void user_shouldNotUpdateOtherUserCluster() {
		setAuthenticatedUser(regularUser1);
		
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
			clusterService.updateStatus(user2Cluster.getId(), ClusterStatus.ACTIVE);
		});
		
		assertTrue(exception.getMessage().contains("acesso negado"));
	}

	@Test
	@DisplayName("user deve poder atualizar seu próprio cluster")
	void user_shouldUpdateOwnCluster() {
		setAuthenticatedUser(regularUser1);
		
		ClusterInstance updated = clusterService.updateStatus(user1Cluster.getId(), ClusterStatus.ACTIVE);
		
		assertEquals(ClusterStatus.ACTIVE, updated.getStatus());
		assertEquals(user1Cluster.getId(), updated.getId());
	}

	@Test
	@DisplayName("admin deve poder atualizar qualquer cluster")
	void admin_shouldUpdateAnyCluster() {
		setAuthenticatedUser(adminUser);
		
		ClusterInstance updated = clusterService.updateStatus(user1Cluster.getId(), ClusterStatus.STOPPED);
		
		assertEquals(ClusterStatus.STOPPED, updated.getStatus());
	}

	@Test
	@DisplayName("user não deve poder deletar cluster de outro usuário")
	void user_shouldNotDeleteOtherUserCluster() {
		setAuthenticatedUser(regularUser1);
		
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
			clusterService.delete(user2Cluster.getId());
		});
		
		assertTrue(exception.getMessage().contains("acesso negado"));
	}

	@Test
	@DisplayName("user deve poder deletar seu próprio cluster")
	void user_shouldDeleteOwnCluster() {
		setAuthenticatedUser(regularUser1);
		
		assertDoesNotThrow(() -> {
			clusterService.delete(user1Cluster.getId());
		});
		
		Optional<ClusterInstance> deleted = clusterService.get(user1Cluster.getId());
		assertFalse(deleted.isPresent());
	}

	@Test
	@DisplayName("cluster criado deve ter ownerId definido")
	void createdCluster_shouldHaveOwnerIdSet() {
		setAuthenticatedUser(regularUser1);
		
		ClusterInstance newCluster = clusterService.create("new-cluster", "webserver-php", null);
		
		assertNotNull(newCluster.getOwnerId());
		assertEquals(regularUser1.getId(), newCluster.getOwnerId());
	}
}

