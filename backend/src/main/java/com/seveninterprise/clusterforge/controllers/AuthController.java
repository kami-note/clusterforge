package com.seveninterprise.clusterforge.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.seveninterprise.clusterforge.dto.AuthenticationResponse;
import com.seveninterprise.clusterforge.dto.LoginRequest;
import com.seveninterprise.clusterforge.dto.RegisterRequest;
import com.seveninterprise.clusterforge.model.Role;
import com.seveninterprise.clusterforge.model.User;
import com.seveninterprise.clusterforge.repository.UserRepository;
import com.seveninterprise.clusterforge.security.JwtProvider;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtProvider jwtProvider;

    public AuthController(UserRepository userRepository, PasswordEncoder passwordEncoder, AuthenticationManager authenticationManager, JwtProvider jwtProvider) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtProvider = jwtProvider;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest registerRequest) {
        // Verifica se o username já existe
        if (userRepository.findByUsername(registerRequest.getUsername()).isPresent()) {
            return ResponseEntity.badRequest().body("Username is already taken!");
        }

        // Verifica se é o primeiro usuário do sistema
        boolean isFirstUser = userRepository.count() == 0;
        
        User user = new User();
        user.setUsername(registerRequest.getUsername());
        user.setPassword(passwordEncoder.encode(registerRequest.getPassword()));
        
        // Primeiro usuário é ADMIN, demais são USER
        user.setRole(isFirstUser ? Role.ADMIN : Role.USER);

        userRepository.save(user);
        
        if (isFirstUser) {
            return ResponseEntity.ok("First user registered successfully as ADMIN!");
        }

        return ResponseEntity.ok("User registered successfully!");
    }

    @PostMapping("/login")
    public ResponseEntity<AuthenticationResponse> login(@RequestBody LoginRequest loginRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getUsername(), loginRequest.getPassword()));

        SecurityContextHolder.getContext().setAuthentication(authentication);
        String jwt = jwtProvider.generateToken((org.springframework.security.core.userdetails.UserDetails) authentication.getPrincipal());

        return ResponseEntity.ok(new AuthenticationResponse(jwt));
    }
}
