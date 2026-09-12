package com.gotham.command.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.gotham.command.entity.AuthProvider;
import com.gotham.command.entity.RefreshToken;
import com.gotham.command.entity.User;
import com.gotham.command.repository.RefreshTokenRepository;
import com.gotham.command.repository.UserRepository;
import com.gotham.command.service.RefreshTokenService;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RefreshTokenServiceTest {

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserRepository userRepository;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setEmail("gordon@gcpd.gov");
        testUser.setFullName("Jim Gordon");
        testUser.setProvider(AuthProvider.LOCAL);
        testUser.setProviderId("local-gordon-1");
        testUser = userRepository.save(testUser);
    }

    @Test
    @DisplayName("Should generate opaque random refresh token and persist with expiration")
    void testGenerateAndSaveRefreshToken() {
        RefreshToken token = refreshTokenService.generateAndSaveRefreshToken(testUser);

        assertThat(token.getId()).isNotNull();
        assertThat(token.getToken()).isNotBlank();
        assertThat(token.getToken()).hasSizeGreaterThanOrEqualTo(32);
        assertThat(token.isRevoked()).isFalse();
        assertThat(token.getExpiresAt()).isAfter(Instant.now());
        assertThat(token.getUser().getId()).isEqualTo(testUser.getId());
    }

    @Test
    @DisplayName("Should validate active unexpired token and reject revoked or expired tokens")
    void testTokenValidation() {
        RefreshToken validToken = refreshTokenService.generateAndSaveRefreshToken(testUser);
        assertThat(refreshTokenService.isValid(validToken.getToken())).isTrue();

        // Revoke
        refreshTokenService.revokeToken(validToken.getToken());
        assertThat(refreshTokenService.isValid(validToken.getToken())).isFalse();

        // Expired token
        RefreshToken expiredToken = refreshTokenService.createRefreshToken(
            testUser,
            "expired-test-token",
            Instant.now().minusSeconds(100)
        );
        assertThat(refreshTokenService.isValid(expiredToken.getToken())).isFalse();
    }

    @Test
    @DisplayName("Should atomically rotate refresh token, revoking old and creating new")
    void testRefreshTokenRotation() {
        RefreshToken initialToken = refreshTokenService.generateAndSaveRefreshToken(testUser);
        String initialTokenString = initialToken.getToken();

        Optional<RefreshToken> rotatedOptional = refreshTokenService.rotateRefreshToken(initialTokenString);
        assertThat(rotatedOptional).isPresent();

        RefreshToken newToken = rotatedOptional.get();
        assertThat(newToken.getToken()).isNotEqualTo(initialTokenString);
        assertThat(newToken.isRevoked()).isFalse();
        assertThat(refreshTokenService.isValid(newToken.getToken())).isTrue();

        // Verify old token is now revoked
        RefreshToken oldTokenReloaded = refreshTokenRepository.findByToken(initialTokenString).orElseThrow();
        assertThat(oldTokenReloaded.isRevoked()).isTrue();
        assertThat(oldTokenReloaded.getRevokedAt()).isNotNull();
        assertThat(refreshTokenService.isValid(initialTokenString)).isFalse();

        // Attempting to rotate an already revoked token must fail
        Optional<RefreshToken> reuseAttempt = refreshTokenService.rotateRefreshToken(initialTokenString);
        assertThat(reuseAttempt).isEmpty();
    }

    @Test
    @DisplayName("Should revoke all active tokens for a specific user upon bulk revocation")
    void testRevokeAllUserTokens() {
        RefreshToken t1 = refreshTokenService.generateAndSaveRefreshToken(testUser);
        RefreshToken t2 = refreshTokenService.generateAndSaveRefreshToken(testUser);

        assertThat(refreshTokenService.isValid(t1.getToken())).isTrue();
        assertThat(refreshTokenService.isValid(t2.getToken())).isTrue();

        refreshTokenService.revokeAllUserTokens(testUser.getId());

        assertThat(refreshTokenService.isValid(t1.getToken())).isFalse();
        assertThat(refreshTokenService.isValid(t2.getToken())).isFalse();
    }
}
