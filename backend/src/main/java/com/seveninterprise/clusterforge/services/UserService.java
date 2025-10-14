package com.seveninterprise.clusterforge.services;

import com.seveninterprise.clusterforge.model.Role;
import com.seveninterprise.clusterforge.model.User;
import com.seveninterprise.clusterforge.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Serviço responsável pelo gerenciamento de usuários
 */
@Service
public class UserService {
    
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    
    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }
    
    /**
     * Cria um novo usuário com credenciais aleatórias
     * @return O usuário criado com suas credenciais
     */
    public UserCredentials createRandomUser() {
        String randomUsername = generateRandomUsername();
        String randomPassword = generateRandomPassword();
        
        User newUser = new User();
        newUser.setUsername(randomUsername);
        newUser.setPassword(passwordEncoder.encode(randomPassword));
        newUser.setRole(Role.USER);
        
        User savedUser = userRepository.save(newUser);
        
        return new UserCredentials(savedUser, randomPassword);
    }
    
    /**
     * Gera um username aleatório
     */
    private String generateRandomUsername() {
        return "user_" + UUID.randomUUID().toString().substring(0, 8);
    }
    
    /**
     * Gera uma senha aleatória
     */
    private String generateRandomPassword() {
        return UUID.randomUUID().toString();
    }
    
    /**
     * Classe interna para retornar usuário e senha em texto plano
     */
    public static class UserCredentials {
        private final User user;
        private final String plainPassword;
        
        public UserCredentials(User user, String plainPassword) {
            this.user = user;
            this.plainPassword = plainPassword;
        }
        
        public User getUser() {
            return user;
        }
        
        public String getPlainPassword() {
            return plainPassword;
        }
        
        public String getUsername() {
            return user.getUsername();
        }
        
        /**
         * Imprime as credenciais em formato bonito no console
         */
        public void printCredentials() {
            System.out.println("╔════════════════════════════════════════════════════════════╗");
            System.out.println("║           NOVO USUÁRIO CRIADO PARA O CLUSTER              ║");
            System.out.println("╠════════════════════════════════════════════════════════════╣");
            System.out.println("║ Username: " + String.format("%-47s", getUsername()) + "║");
            System.out.println("║ Password: " + String.format("%-47s", plainPassword) + "║");
            System.out.println("╚════════════════════════════════════════════════════════════╝");
        }
    }
}


