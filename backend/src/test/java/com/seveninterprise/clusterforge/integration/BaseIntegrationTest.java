package com.seveninterprise.clusterforge.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seveninterprise.clusterforge.model.Role;
import com.seveninterprise.clusterforge.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Base class for integration tests with common setup and utilities.
 * 
 * Provides:
 * - MockMvc setup for web layer testing
 * - Common test user creation
 * - JSON serialization utilities
 * - Database transaction rollback
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("integration")
@Transactional
public abstract class BaseIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    protected User testAdmin;
    protected User testClient;

    @BeforeEach
    void setUpTestUsers() {
        // Create test admin user
        testAdmin = new User();
        testAdmin.setId(1L);
        testAdmin.setUsername("test_admin");
        testAdmin.setRole(Role.ADMIN);

        // Create test client user
        testClient = new User();
        testClient.setId(2L);
        testClient.setUsername("test_client");
        testClient.setRole(Role.USER);
    }

    /**
     * Convert object to JSON string for request body
     */
    protected String asJsonString(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException("Error converting object to JSON", e);
        }
    }

    /**
     * Convert JSON string to object
     */
    protected <T> T fromJsonString(String json, Class<T> clazz) {
        try {
            return objectMapper.readValue(json, clazz);
        } catch (Exception e) {
            throw new RuntimeException("Error converting JSON to object", e);
        }
    }
}
