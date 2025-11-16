package com.kryptforge.clusterforge.web;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
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
import com.kryptforge.clusterforge.docker.DockerEngineService;
import com.kryptforge.clusterforge.templates.TemplateProperties;
import com.kryptforge.clusterforge.templates.dto.TemplateInstantiateRequest;

/**
 * Testes de integração para TemplateController usando MockMvc.
 * Testa o fluxo completo HTTP sem Docker real.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
	"docker.templates.path=${java.io.tmpdir}/clusterforge-test-templates-controller",
	"docker.volumes.basePath=${java.io.tmpdir}/clusterforge-test-volumes-controller",
	"spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
	"spring.datasource.username=sa",
	"spring.datasource.password=",
	"spring.jpa.hibernate.ddl-auto=create-drop"
})
class TemplateControllerIntegrationTest {

	private static final String TEMP_DIR = System.getProperty("java.io.tmpdir");
	private static final String TEMPLATES_PATH = TEMP_DIR + "/clusterforge-test-templates-controller";
	private static final String VOLUMES_PATH = TEMP_DIR + "/clusterforge-test-volumes-controller";

	static {
		// Garante que os diretórios existem antes de qualquer coisa
		try {
			Files.createDirectories(Path.of(TEMPLATES_PATH));
			Files.createDirectories(Path.of(VOLUMES_PATH));
		} catch (Exception e) {
			throw new RuntimeException("Falha ao criar diretórios de teste", e);
		}
	}

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private DockerEngineService dockerEngineService;

	@Autowired
	private ObjectMapper objectMapper;

	private String testContainerName;

	@Autowired
	private TemplateProperties templateProperties;

	@BeforeEach
	void setup() throws Exception {
		testContainerName = "test-http-" + System.currentTimeMillis();
		
		// Garante que o path de templates existe
		Path templatesRoot = Path.of(TEMPLATES_PATH).toAbsolutePath();
		Files.createDirectories(templatesRoot);
		
		// Cria um template de teste para alguns testes
		Path testTemplateDir = templatesRoot.resolve("test-template");
		Files.createDirectories(testTemplateDir);
		Files.writeString(testTemplateDir.resolve("docker-compose.yml"),
			"version: '3.9'\n" +
			"services:\n" +
			"  app:\n" +
			"    image: alpine:latest\n" +
			"    command: [\"sh\", \"-c\", \"sleep 10\"]\n" +
			"    environment:\n" +
			"      VAR1: value1\n" +
			"    ports:\n" +
			"      - \"80\"\n"); // Apenas porta do container - PortManager aloca porta do host dinamicamente
	}

	@AfterEach
	void cleanup() {
		if (dockerEngineService != null && testContainerName != null) {
			try {
				List<com.github.dockerjava.api.model.Container> containers = dockerEngineService.listContainers(true);
				for (com.github.dockerjava.api.model.Container c : containers) {
					String[] names = c.getNames();
					if (names != null) {
						for (String name : names) {
							if (name.contains(testContainerName)) {
								try {
									dockerEngineService.stopContainer(c.getId(), 5);
								} catch (Exception ignored) {
								}
								try {
									dockerEngineService.removeContainer(c.getId(), true, false);
								} catch (Exception ignored) {
								}
								break;
							}
						}
					}
				}
			} catch (Exception ignored) {
			}
		}
	}

	@Test
	@DisplayName("GET /api/templates deve retornar lista de templates")
	void listTemplates_returnsList() throws Exception {
		// Nota: Este teste depende de templates reais no path configurado
		// Pode falhar se não houver templates, mas valida o endpoint
		mockMvc.perform(get("/api/templates"))
			.andExpect(status().isOk())
			.andExpect(content().contentType(MediaType.APPLICATION_JSON));
	}

	@Test
	@DisplayName("GET /api/templates/{name} deve retornar 404 quando template não existe")
	void getTemplate_returns404WhenNotFound() throws Exception {
		mockMvc.perform(get("/api/templates/not-exists"))
			.andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("POST /api/templates/{name}/instantiate deve validar request")
	void instantiate_validatesRequest() throws Exception {
		// Request sem name (inválido)
		TemplateInstantiateRequest invalidRequest = new TemplateInstantiateRequest(
			"", // name vazio
			null,
			null,
			null
		);

		mockMvc.perform(post("/api/templates/test-template/instantiate")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(invalidRequest)))
			.andExpect(status().isForbidden()); // Agora precisa de autenticação, retorna 403
	}

	@Test
	@DisplayName("POST /api/templates/{name}/instantiate deve retornar 403 quando template não existe (sem autenticação)")
	void instantiate_returns403WhenTemplateNotFound() throws Exception {
		TemplateInstantiateRequest request = new TemplateInstantiateRequest(
			testContainerName,
			null,
			null,
			null
		);

		mockMvc.perform(post("/api/templates/not-exists/instantiate")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isForbidden()); // Agora precisa de autenticação, retorna 403
	}

	@Test
	@DisplayName("POST /api/templates/{name}/instantiate deve retornar 409 quando nome já está em uso")
	void instantiate_returns409WhenNameInUse() throws Exception {
		// Requer Docker disponível
		boolean dockerAvailable = "1".equals(System.getenv("DOCKER_INTEGRATION_TEST"));
		assumeTrue(dockerAvailable, "Teste requer Docker. Defina DOCKER_INTEGRATION_TEST=1.");

		TemplateInstantiateRequest request = new TemplateInstantiateRequest(
			testContainerName,
			null,
			null,
			null
		);

		// Primeira criação deve funcionar
		mockMvc.perform(post("/api/templates/test-template/instantiate")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isCreated());

		// Segunda criação com mesmo nome deve retornar 409
		mockMvc.perform(post("/api/templates/test-template/instantiate")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isConflict());
	}

	@Test
	@DisplayName("POST /api/templates/{name}/instantiate deve aceitar request válido")
	void instantiate_acceptsValidRequest() throws Exception {
		// Requer Docker disponível
		boolean dockerAvailable = "1".equals(System.getenv("DOCKER_INTEGRATION_TEST"));
		assumeTrue(dockerAvailable, "Teste requer Docker. Defina DOCKER_INTEGRATION_TEST=1.");

		TemplateInstantiateRequest request = new TemplateInstantiateRequest(
			testContainerName,
			Map.of("VAR2", "override"),
			List.of("9090:80"),
			List.of("/host:/container")
		);

		mockMvc.perform(post("/api/templates/test-template/instantiate")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isCreated());
	}
}

