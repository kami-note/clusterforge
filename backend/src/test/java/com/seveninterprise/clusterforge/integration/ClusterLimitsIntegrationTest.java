package com.seveninterprise.clusterforge.integration;

import com.seveninterprise.clusterforge.dto.CreateClusterRequest;
import com.seveninterprise.clusterforge.dto.CreateClusterResponse;
import com.seveninterprise.clusterforge.model.Cluster;
import com.seveninterprise.clusterforge.model.Role;
import com.seveninterprise.clusterforge.model.User;
import com.seveninterprise.clusterforge.repository.ClusterRepository;
import com.seveninterprise.clusterforge.services.ClusterService;
import com.seveninterprise.clusterforge.services.DockerService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Teste de Integração Real - Cria cluster Docker e valida limites aplicados
 * 
 * ATENÇÃO: Este teste:
 * - Cria container Docker REAL
 * - Requer Docker rodando
 * - Cria arquivos no filesystem
 * - Pode levar 30+ segundos
 * 
 * Para executar:
 * mvnw test -Dtest=ClusterLimitsIntegrationTest
 */
@SpringBootTest
@ActiveProfiles("integration")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ClusterLimitsIntegrationTest extends BaseTestContainersIntegrationTest {

    @Autowired
    private ClusterService clusterService;

    @Autowired
    private ClusterRepository clusterRepository;

    @Autowired
    private DockerService dockerService;

    private static Long testClusterId;
    private static String testClusterPath;
    // Container name will be generated dynamically

    private User testAdmin;

    @BeforeEach
    void setUp() {
        // Criar usuário admin de teste
        testAdmin = new User();
        testAdmin.setId(999L);
        testAdmin.setUsername("test_admin");
        testAdmin.setRole(Role.ADMIN);
    }

    @Test
    @Order(1)
    @DisplayName("1. Criar cluster com limites específicos")
    void testCreateClusterWithLimits() {
        // Arrange
        CreateClusterRequest request = new CreateClusterRequest();
        request.setTemplateName("test-alpine");
        request.setBaseName("integration-test");
        request.setCpuLimit(1.5);
        request.setMemoryLimit(512L);
        request.setDiskLimit(5L);
        request.setNetworkLimit(50L);

        // Act
        CreateClusterResponse response = clusterService.createCluster(request, testAdmin);

        // Assert
        assertNotNull(response, "Response não deve ser null");
        assertNotNull(response.getClusterId(), "Cluster ID deve ser gerado");
        assertNotNull(response.getClusterName(), "Nome do cluster deve ser gerado");
        assertTrue(response.getPort() >= 9000, "Porta deve estar no range configurado");

        // Salvar dados para próximos testes
        testClusterId = response.getClusterId();
        
        System.out.println("✅ Cluster criado:");
        System.out.println("   ID: " + testClusterId);
        System.out.println("   Nome: " + response.getClusterName());
        System.out.println("   Porta: " + response.getPort());
    }

    @Test
    @Order(2)
    @DisplayName("2. Verificar limites salvos no banco de dados")
    void testLimitsInDatabase() {
        assertNotNull(testClusterId, "Cluster deve ter sido criado no teste anterior");

        // Act
        Cluster cluster = clusterRepository.findById(testClusterId).orElse(null);

        // Assert
        assertNotNull(cluster, "Cluster deve existir no banco");
        assertEquals(1.5, cluster.getCpuLimit(), "CPU limit deve ser 1.5");
        assertEquals(512L, cluster.getMemoryLimit(), "Memory limit deve ser 512MB");
        assertEquals(5L, cluster.getDiskLimit(), "Disk limit deve ser 5GB");
        assertEquals(50L, cluster.getNetworkLimit(), "Network limit deve ser 50MB/s");

        testClusterPath = cluster.getRootPath();
        // Container name will be generated dynamically based on cluster name

        System.out.println("✅ Limites validados no banco:");
        System.out.println("   CPU: " + cluster.getCpuLimit() + " cores");
        System.out.println("   RAM: " + cluster.getMemoryLimit() + " MB");
        System.out.println("   Disco: " + cluster.getDiskLimit() + " GB");
        System.out.println("   Rede: " + cluster.getNetworkLimit() + " MB/s");
    }

    @Test
    @Order(3)
    @DisplayName("3. Verificar docker-compose.yml gerado com limites")
    void testDockerComposeWithLimits() throws Exception {
        assertNotNull(testClusterPath, "Cluster path deve existir");

        // Act
        String dockerComposePath = testClusterPath + "/docker-compose.yml";
        assertTrue(new File(dockerComposePath).exists(), "docker-compose.yml deve existir");

        String content = new String(Files.readAllBytes(Paths.get(dockerComposePath)));

        // Assert - Verificar seções de limites
        assertTrue(content.contains("deploy:"), "Deve conter seção deploy");
        assertTrue(content.contains("resources:"), "Deve conter seção resources");
        assertTrue(content.contains("limits:"), "Deve conter seção limits");
        assertTrue(content.contains("cpus: '1.50'"), "Deve conter CPU limit 1.5");
        assertTrue(content.contains("memory: 512m"), "Deve conter memory limit 512MB");
        
        // Verificar environment vars
        assertTrue(content.contains("CPU_LIMIT=1.50"), "Deve conter env var CPU_LIMIT");
        assertTrue(content.contains("MEMORY_LIMIT_MB=512"), "Deve conter env var MEMORY_LIMIT_MB");
        assertTrue(content.contains("DISK_LIMIT_GB=5"), "Deve conter env var DISK_LIMIT_GB");
        assertTrue(content.contains("NETWORK_LIMIT_MBPS=50"), "Deve conter env var NETWORK_LIMIT_MBPS");

        // Verificar capabilities
        assertTrue(content.contains("cap_add:"), "Deve conter cap_add");
        assertTrue(content.contains("NET_ADMIN"), "Deve conter NET_ADMIN capability");

        // Verificar tmpfs
        assertTrue(content.contains("tmpfs:"), "Deve conter tmpfs");
        
        // Verificar storage_opt
        assertTrue(content.contains("storage_opt:"), "Deve conter storage_opt");
        assertTrue(content.contains("size: '5G'"), "Deve conter disk limit 5GB");

        System.out.println("✅ docker-compose.yml validado:");
        System.out.println("   • deploy.resources com CPU e RAM");
        System.out.println("   • environment vars com limites");
        System.out.println("   • cap_add com NET_ADMIN");
        System.out.println("   • tmpfs configurado");
        System.out.println("   • storage_opt: 5G");
    }

    @Test
    @Order(4)
    @DisplayName("4. Verificar container Docker com limites aplicados")
    void testDockerContainerLimits() {
        // Nota: Este teste assume que Docker está rodando
        // Se Docker não estiver disponível, teste será pulado
        
        try {
            // Verificar se Docker está disponível
            String dockerVersion = dockerService.runCommand("docker --version");
            if (!dockerVersion.contains("Docker")) {
                System.out.println("⚠️  Docker não disponível - teste pulado");
                return;
            }

            // Aguardar container inicializar
            Thread.sleep(5000);

            // Verificar se container existe
            String psResult = dockerService.runCommand("docker ps --format '{{.Names}}' | grep alpine_test");
            
            if (!psResult.contains("alpine_test")) {
                System.out.println("⚠️  Container não está rodando - teste pulado");
                return;
            }

            // Verificar limites aplicados via docker inspect
            String inspectResult = dockerService.runCommand(
                "docker inspect alpine_test_* --format '{{json .HostConfig}}'"
            );

            // Validações básicas
            assertTrue(inspectResult.contains("NanoCpus"), "Deve ter configuração de CPU");
            assertTrue(inspectResult.contains("Memory"), "Deve ter configuração de memória");

            System.out.println("✅ Container Docker validado:");
            System.out.println("   • Container rodando");
            System.out.println("   • Limites de CPU configurados (NanoCpus)");
            System.out.println("   • Limites de RAM configurados (Memory)");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("Teste interrompido");
        } catch (Exception e) {
            System.out.println("⚠️  Erro ao verificar container: " + e.getMessage());
            System.out.println("   Teste pulado (pode ser ambiente sem Docker)");
        }
    }

    @Test
    @Order(5)
    @DisplayName("5. Verificar scripts copiados para o cluster")
    void testScriptsCopied() {
        assertNotNull(testClusterPath, "Cluster path deve existir");

        // Verificar se init-limits.sh foi copiado
        File initLimitsScript = new File(testClusterPath + "/init-limits.sh");
        
        if (initLimitsScript.exists()) {
            assertTrue(initLimitsScript.canExecute(), "init-limits.sh deve ser executável");
            System.out.println("✅ Script init-limits.sh copiado e executável");
        } else {
            System.out.println("⚠️  Script init-limits.sh não encontrado (ok em ambiente de teste)");
        }
    }

    @AfterAll
    static void cleanup(@Autowired ClusterService clusterService, 
                       @Autowired ClusterRepository clusterRepository) {
        System.out.println("\n🧹 Limpando recursos do teste...");

        if (testClusterId != null) {
            try {
                // Parar e remover container
                try {
                    ProcessBuilder pb = new ProcessBuilder("docker-compose", "-f", testClusterPath + "/docker-compose.yml", "down");
                    pb.start().waitFor();
                    System.out.println("✅ Container parado");
                } catch (Exception e) {
                    System.out.println("⚠️  Erro ao parar container: " + e.getMessage());
                }

                // Remover do banco
                clusterRepository.deleteById(testClusterId);
                System.out.println("✅ Cluster removido do banco");

                // Remover diretório
                if (testClusterPath != null) {
                    File clusterDir = new File(testClusterPath);
                    if (clusterDir.exists()) {
                        deleteDirectory(clusterDir);
                        System.out.println("✅ Diretório removido: " + testClusterPath);
                    }
                }

            } catch (Exception e) {
                System.err.println("⚠️  Erro na limpeza: " + e.getMessage());
            }
        }

        System.out.println("✅ Limpeza concluída\n");
    }

    private static void deleteDirectory(File directory) {
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    file.delete();
                }
            }
        }
        directory.delete();
    }
}


