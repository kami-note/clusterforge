package com.seveninterprise.clusterforge.services;

import com.seveninterprise.clusterforge.model.RefreshToken;
import com.seveninterprise.clusterforge.model.User;
import com.seveninterprise.clusterforge.repository.RefreshTokenRepository;
import com.seveninterprise.clusterforge.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

@Service
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${jwt.refresh.expiration.ms:1209600000}")
    private long refreshExpirationMs;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository, UserRepository userRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public String createRefreshTokenForUser(String username) {
        User user = userRepository.findByUsername(username).orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado"));

        byte[] randomBytes = new byte[64];
        secureRandom.nextBytes(randomBytes);
        String tokenPlain = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        String tokenHash = sha256(tokenPlain);

        RefreshToken entity = new RefreshToken();
        entity.setUser(user);
        entity.setTokenHash(tokenHash);
        entity.setExpiresAt(Instant.now().plusMillis(refreshExpirationMs));
        entity.setRevoked(false);

        refreshTokenRepository.save(entity);
        return tokenPlain;
    }

    @Transactional(readOnly = true)
    public Optional<User> validateAndGetUserFromRefreshToken(String refreshTokenPlain) {
        String tokenHash = sha256(refreshTokenPlain);
        return refreshTokenRepository.findByTokenHash(tokenHash)
                .filter(rt -> !rt.isRevoked() && rt.getExpiresAt().isAfter(Instant.now()))
                .map(RefreshToken::getUser);
    }

    @Transactional
    public boolean revokeRefreshToken(String refreshTokenPlain) {
        String tokenHash = sha256(refreshTokenPlain);
        Optional<RefreshToken> rtOpt = refreshTokenRepository.findByTokenHash(tokenHash);
        if (rtOpt.isPresent()) {
            RefreshToken rt = rtOpt.get();
            rt.setRevoked(true);
            refreshTokenRepository.save(rt);
            return true;
        }
        return false;
    }

    @Transactional
    public void revokeAllForUser(Long userId) {
        refreshTokenRepository.deleteByUserId(userId);
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 não disponível", e);
        }
    }
}


