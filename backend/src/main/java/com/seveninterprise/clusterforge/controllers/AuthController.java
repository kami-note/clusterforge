package com.seveninterprise.clusterforge.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Value;
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
import com.seveninterprise.clusterforge.dto.TokenRefreshRequest;
import com.seveninterprise.clusterforge.model.Role;
import com.seveninterprise.clusterforge.model.User;
import com.seveninterprise.clusterforge.repository.UserRepository;
import com.seveninterprise.clusterforge.security.JwtProvider;
import com.seveninterprise.clusterforge.services.RefreshTokenService;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtProvider jwtProvider;
    private final RefreshTokenService refreshTokenService;

    @Value("${jwt.access.expiration.ms:3600000}")
    private long accessExpirationMs;

    public AuthController(UserRepository userRepository, PasswordEncoder passwordEncoder, AuthenticationManager authenticationManager, JwtProvider jwtProvider, RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtProvider = jwtProvider;
        this.refreshTokenService = refreshTokenService;
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
        User user = userRepository.findByUsername(loginRequest.getUsername())
                .orElseThrow(() -> new IllegalStateException("Usuário autenticado, mas não encontrado no repositório"));

        String jwt = jwtProvider.generateToken(
                (org.springframework.security.core.userdetails.UserDetails) authentication.getPrincipal(),
                user.getId());

        String refresh = refreshTokenService.createRefreshTokenForUser(user.getUsername());
        return ResponseEntity.ok(new AuthenticationResponse(jwt, refresh, accessExpirationMs));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthenticationResponse> refresh(@RequestBody TokenRefreshRequest request) {
        if (request == null || request.getRefreshToken() == null || request.getRefreshToken().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        return refreshTokenService.validateAndGetUserFromRefreshToken(request.getRefreshToken())
                .map(user -> {
                    org.springframework.security.core.userdetails.UserDetails userDetails =
                            new org.springframework.security.core.userdetails.User(user.getUsername(), user.getPassword(),
                                    java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
                    String newAccess = jwtProvider.generateToken(userDetails, user.getId());
                    // Rotaciona o refresh token (revoga o antigo e emite outro)
                    refreshTokenService.revokeRefreshToken(request.getRefreshToken());
                    String newRefresh = refreshTokenService.createRefreshTokenForUser(user.getUsername());
                    return ResponseEntity.ok(new AuthenticationResponse(newAccess, newRefresh, accessExpirationMs));
                })
                .orElseGet(() -> ResponseEntity.status(401).build());
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestBody(required = false) TokenRefreshRequest request) {
        // Se enviado um refreshToken específico, revoga apenas ele. Caso contrário, limpa todos do usuário autenticado (se houver contexto)
        if (request != null && request.getRefreshToken() != null && !request.getRefreshToken().isBlank()) {
            refreshTokenService.revokeRefreshToken(request.getRefreshToken());
            return ResponseEntity.ok().build();
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getName() != null) {
            User user = userRepository.findByUsername(auth.getName()).orElse(null);
            if (user != null) {
                refreshTokenService.revokeAllForUser(user.getId());
            }
        }
        return ResponseEntity.ok().build();
    }
}
