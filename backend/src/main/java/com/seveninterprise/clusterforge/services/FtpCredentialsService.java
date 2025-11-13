package com.seveninterprise.clusterforge.services;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Serviço para gerenciamento de credenciais FTP
 * 
 * Responsabilidades:
 * - Geração de usuários e senhas FTP aleatórios
 * - Criptografia de senhas FTP
 * - Validação de credenciais
 */
@Service
public class FtpCredentialsService {
    
    private final PasswordEncoder passwordEncoder;
    
    public FtpCredentialsService(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }
    
    /**
     * Gera credenciais FTP aleatórias para um cluster
     * @return Credenciais FTP (username e password em texto plano)
     */
    public FtpCredentials generateFtpCredentials() {
        String username = generateFtpUsername();
        String password = generateFtpPassword();
        
        return new FtpCredentials(username, password);
    }
    
    /**
     * Gera um username FTP aleatório
     * Formato: ftp_XXXX onde XXXX são caracteres aleatórios
     */
    private String generateFtpUsername() {
        return "ftp_" + UUID.randomUUID().toString().substring(0, 8);
    }
    
    /**
     * Gera uma senha FTP aleatória
     * Usa UUID para garantir aleatoriedade
     */
    private String generateFtpPassword() {
        // Gera senha mais curta e amigável (12 caracteres)
        String uuid = UUID.randomUUID().toString().replace("-", "");
        return uuid.substring(0, 12);
    }
    
    /**
     * Criptografa uma senha FTP
     * @param plainPassword Senha em texto plano
     * @return Senha criptografada
     */
    public String encryptFtpPassword(String plainPassword) {
        return passwordEncoder.encode(plainPassword);
    }
    
    /**
     * Verifica se uma senha FTP corresponde à senha criptografada
     * @param plainPassword Senha em texto plano
     * @param encryptedPassword Senha criptografada
     * @return true se as senhas correspondem
     */
    public boolean verifyFtpPassword(String plainPassword, String encryptedPassword) {
        return passwordEncoder.matches(plainPassword, encryptedPassword);
    }
    
    /**
     * Classe interna para retornar credenciais FTP
     */
    public static class FtpCredentials {
        private final String username;
        private final String plainPassword;
        
        public FtpCredentials(String username, String plainPassword) {
            this.username = username;
            this.plainPassword = plainPassword;
        }
        
        public String getUsername() {
            return username;
        }
        
        public String getPlainPassword() {
            return plainPassword;
        }
    }
}

