package com.seveninterprise.clusterforge.integration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Teste de integração simples para verificar se a configuração básica está funcionando.
 */
@SpringBootTest
@ActiveProfiles("test")
class SimpleIntegrationTest {

    @Test
    void contextLoads() {
        // Teste simples para verificar se o contexto Spring carrega
        assertTrue(true, "Context should load successfully");
    }

    @Test
    void basicAssertion() {
        // Teste básico para verificar se os testes estão funcionando
        String message = "Hello Integration Test";
        assertTrue(message.contains("Integration"), "Message should contain 'Integration'");
    }
}
