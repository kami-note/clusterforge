package com.kryptforge.clusterforge.security;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Serviço para criptografia e descriptografia de senhas usando AES-256-GCM.
 * 
 * <p>
 * Características de segurança:
 * </p>
 * <ul>
 * <li>AES-256-GCM - Criptografia autenticada (confidencialidade +
 * integridade)</li>
 * <li>IV (Initialization Vector) único para cada criptografia</li>
 * <li>Tag de autenticação de 128 bits</li>
 * </ul>
 * 
 * <p>
 * Formato do dado criptografado: Base64(IV + ciphertext + authTag)
 * </p>
 */
@Service
public class PasswordEncryptionService {

    private static final Logger log = LoggerFactory.getLogger(PasswordEncryptionService.class);

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12; // 96 bits - recomendado para GCM
    private static final int GCM_TAG_LENGTH = 128; // 128 bits - máximo para GCM

    private final SecretKey secretKey;
    private final SecureRandom secureRandom;

    public PasswordEncryptionService(
            @Value("${clusterforge.security.encryption.key:}") String encryptionKey) {

        // Se não configurado, gera chave aleatória (não persistente - apenas para dev)
        if (encryptionKey == null || encryptionKey.isBlank()) {
            log.warn("⚠️ Chave de criptografia não configurada! Gerando chave temporária. " +
                    "Configure 'clusterforge.security.encryption.key' em produção.");
            byte[] randomKey = new byte[32]; // 256 bits
            new SecureRandom().nextBytes(randomKey);
            this.secretKey = new SecretKeySpec(randomKey, "AES");
        } else {
            // Decodifica chave Base64 da configuração
            byte[] keyBytes = Base64.getDecoder().decode(encryptionKey);
            if (keyBytes.length != 32) {
                throw new IllegalArgumentException(
                        "Chave de criptografia deve ter 32 bytes (256 bits). Recebido: " + keyBytes.length);
            }
            this.secretKey = new SecretKeySpec(keyBytes, "AES");
            log.info("✅ Serviço de criptografia inicializado com chave configurada");
        }

        this.secureRandom = new SecureRandom();
    }

    /**
     * Criptografa uma senha usando AES-256-GCM.
     * 
     * @param plainText senha em texto plano
     * @return senha criptografada em Base64 (formato: IV + ciphertext + authTag)
     */
    public String encrypt(String plainText) {
        if (plainText == null || plainText.isEmpty()) {
            return plainText;
        }

        try {
            // Gera IV aleatório único para cada criptografia
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            // Configura cipher
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

            // Criptografa
            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            // Combina IV + ciphertext (IV é necessário para descriptografar)
            byte[] combined = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);

            return Base64.getEncoder().encodeToString(combined);

        } catch (Exception e) {
            log.error("Erro ao criptografar: {}", e.getMessage());
            throw new RuntimeException("Falha ao criptografar senha", e);
        }
    }

    /**
     * Descriptografa uma senha usando AES-256-GCM.
     * 
     * @param encryptedText senha criptografada em Base64
     * @return senha em texto plano
     */
    public String decrypt(String encryptedText) {
        if (encryptedText == null || encryptedText.isEmpty()) {
            return encryptedText;
        }

        try {
            // Decodifica Base64
            byte[] combined = Base64.getDecoder().decode(encryptedText);

            // Extrai IV e ciphertext
            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] cipherText = new byte[combined.length - GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, iv.length);
            System.arraycopy(combined, iv.length, cipherText, 0, cipherText.length);

            // Configura cipher
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

            // Descriptografa
            byte[] plainText = cipher.doFinal(cipherText);

            return new String(plainText, StandardCharsets.UTF_8);

        } catch (Exception e) {
            log.error("Erro ao descriptografar: {}", e.getMessage());
            throw new RuntimeException("Falha ao descriptografar senha", e);
        }
    }

    /**
     * Verifica se um texto parece estar criptografado (é Base64 válido com tamanho
     * adequado).
     * 
     * @param text texto a verificar
     * @return true se parece criptografado
     */
    public boolean isEncrypted(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }

        try {
            byte[] decoded = Base64.getDecoder().decode(text);
            // Deve ter pelo menos IV (12 bytes) + algum conteúdo + tag (16 bytes)
            return decoded.length > GCM_IV_LENGTH + 16;
        } catch (IllegalArgumentException e) {
            // Não é Base64 válido
            return false;
        }
    }
}
