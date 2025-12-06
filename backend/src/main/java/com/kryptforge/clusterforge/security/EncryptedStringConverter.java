package com.kryptforge.clusterforge.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Converter JPA para criptografar/descriptografar automaticamente campos de
 * senha.
 * 
 * <p>
 * Uso:
 * </p>
 * 
 * <pre>
 * {@literal @}Convert(converter = EncryptedStringConverter.class)
 * private String ftpPassword;
 * </pre>
 * 
 * <p>
 * Características:
 * </p>
 * <ul>
 * <li>Criptografa automaticamente ao salvar no banco</li>
 * <li>Descriptografa automaticamente ao ler do banco</li>
 * <li>Backward compatibility: senhas em plain text continuam funcionando</li>
 * <li>Prefixo "ENC:" identifica dados criptografados</li>
 * </ul>
 */
@Component
@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private static final Logger log = LoggerFactory.getLogger(EncryptedStringConverter.class);
    private static final String ENCRYPTED_PREFIX = "ENC:";

    private static PasswordEncryptionService encryptionService;

    /**
     * Injeção via setter estático para permitir uso em converter JPA.
     * JPA instancia converters diretamente, então não podemos usar injeção no
     * construtor.
     */
    @Autowired
    public void setEncryptionService(PasswordEncryptionService service) {
        EncryptedStringConverter.encryptionService = service;
        log.debug("EncryptedStringConverter configurado com PasswordEncryptionService");
    }

    /**
     * Converte atributo da entidade para coluna do banco de dados.
     * Criptografa o valor se ainda não estiver criptografado.
     * 
     * @param attribute valor do atributo (senha em plain text)
     * @return valor criptografado com prefixo "ENC:"
     */
    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return attribute;
        }

        // Se já está criptografado (tem prefixo), não criptografa novamente
        if (attribute.startsWith(ENCRYPTED_PREFIX)) {
            return attribute;
        }

        try {
            if (encryptionService == null) {
                log.warn("EncryptionService não inicializado, salvando em plain text");
                return attribute;
            }

            String encrypted = encryptionService.encrypt(attribute);
            return ENCRYPTED_PREFIX + encrypted;
        } catch (Exception e) {
            log.error("Erro ao criptografar, salvando em plain text: {}", e.getMessage());
            return attribute;
        }
    }

    /**
     * Converte coluna do banco de dados para atributo da entidade.
     * Descriptografa o valor se estiver criptografado.
     * 
     * @param dbData valor do banco (pode ser criptografado ou plain text)
     * @return senha descriptografada
     */
    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isEmpty()) {
            return dbData;
        }

        // Se não tem prefixo, é plain text (backward compatibility)
        if (!dbData.startsWith(ENCRYPTED_PREFIX)) {
            log.debug("Dado sem prefixo ENC:, tratando como plain text (backward compatibility)");
            return dbData;
        }

        try {
            if (encryptionService == null) {
                log.warn("EncryptionService não inicializado, retornando dado criptografado como está");
                return dbData;
            }

            // Remove prefixo e descriptografa
            String encryptedData = dbData.substring(ENCRYPTED_PREFIX.length());
            return encryptionService.decrypt(encryptedData);
        } catch (Exception e) {
            log.error("Erro ao descriptografar, retornando dado como está: {}", e.getMessage());
            return dbData;
        }
    }
}
