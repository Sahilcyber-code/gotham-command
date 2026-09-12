package com.gotham.command.service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gotham.command.entity.RefreshToken;
import com.gotham.command.entity.User;
import com.gotham.command.repository.RefreshTokenRepository;

@Service
@Transactional(readOnly = true)
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.auth.refresh-token-expiration:604800000}")
    private long refreshTokenExpirationMs;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    private String generateSecureTokenString() {
        byte[] randomBytes = new byte[48];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    @Transactional
    public RefreshToken generateAndSaveRefreshToken(User user) {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setToken(generateSecureTokenString());
        refreshToken.setExpiresAt(Instant.now().plusMillis(refreshTokenExpirationMs));
        refreshToken.setRevoked(false);

        return refreshTokenRepository.save(refreshToken);
    }

    @Transactional
    public RefreshToken createRefreshToken(User user, String token, Instant expiresAt) {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setToken(token != null ? token : generateSecureTokenString());
        refreshToken.setExpiresAt(expiresAt != null ? expiresAt : Instant.now().plusMillis(refreshTokenExpirationMs));
        refreshToken.setRevoked(false);

        return refreshTokenRepository.save(refreshToken);
    }

    public Optional<RefreshToken> findByToken(String token) {
        return refreshTokenRepository.findByToken(token);
    }

    public List<RefreshToken> findActiveTokensByUser(UUID userId) {
        return refreshTokenRepository.findByUserIdAndRevokedFalse(userId);
    }

    public boolean isValid(String token) {
        return refreshTokenRepository.findByToken(token)
            .map(t -> !t.isRevoked() && t.getExpiresAt().isAfter(Instant.now()))
            .orElse(false);
    }

    @Transactional
    public Optional<RefreshToken> rotateRefreshToken(String oldTokenString) {
        if (oldTokenString == null || oldTokenString.isBlank()) {
            return Optional.empty();
        }

        Optional<RefreshToken> optionalOldToken = refreshTokenRepository.findByToken(oldTokenString);
        if (optionalOldToken.isEmpty()) {
            return Optional.empty();
        }

        RefreshToken oldToken = optionalOldToken.get();
        if (oldToken.isRevoked() || oldToken.getExpiresAt().isBefore(Instant.now())) {
            return Optional.empty();
        }

        // Revoke the old token
        oldToken.setRevoked(true);
        oldToken.setRevokedAt(Instant.now());
        refreshTokenRepository.save(oldToken);

        // Generate and persist brand new replacement token
        RefreshToken newToken = generateAndSaveRefreshToken(oldToken.getUser());
        return Optional.of(newToken);
    }

    @Transactional
    public void revokeToken(String token) {
        refreshTokenRepository.findByToken(token).ifPresent(t -> {
            t.setRevoked(true);
            t.setRevokedAt(Instant.now());
            refreshTokenRepository.save(t);
        });
    }

    @Transactional
    public void revokeAllUserTokens(UUID userId) {
        List<RefreshToken> activeTokens = refreshTokenRepository.findByUserIdAndRevokedFalse(userId);
        Instant now = Instant.now();
        for (RefreshToken t : activeTokens) {
            t.setRevoked(true);
            t.setRevokedAt(now);
        }
        refreshTokenRepository.saveAll(activeTokens);
    }
}
