package com.seveninterprise.clusterforge.integration;

import com.seveninterprise.clusterforge.model.Role;
import com.seveninterprise.clusterforge.model.User;
import org.junit.jupiter.api.BeforeEach;

import java.time.Duration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base class for integration tests using TestContainers.
 * 
 * Provides:
 * - MySQL TestContainer setup
 * - Dynamic property configuration
 * - Database connection management
 * - Transaction rollback for test isolation
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("integration")
@Transactional
public abstract class BaseTestContainersIntegrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test")
            .withReuse(false)  // Não reutilizar para evitar conflitos
            .withCreateContainerCmdModifier(cmd -> cmd.withName("testcontainers-mysql-" + System.currentTimeMillis()))
            .withStartupTimeout(Duration.ofMinutes(2));  // Aumentar timeout

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    }

    protected User testUser;
    protected User testAdmin;

    @BeforeEach
    void setUp() {
        // Wait for container to be ready
        if (!mysql.isRunning()) {
            mysql.start();
        }
        
        // Create test users
        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("testuser");
        testUser.setRole(Role.USER);

        testAdmin = new User();
        testAdmin.setId(2L);
        testAdmin.setUsername("testadmin");
        testAdmin.setRole(Role.ADMIN);
    }

    /**
     * Get the MySQL container instance for direct access if needed
     */
    protected MySQLContainer<?> getMySQLContainer() {
        return mysql;
    }

    /**
     * Execute SQL directly on the test database
     */
    protected void executeSql(String sql) {
        try (var connection = mysql.createConnection("");
             var statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (Exception e) {
            throw new RuntimeException("Error executing SQL: " + sql, e);
        }
    }
}
