package com.gotham.command.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gotham.command.entity.AuthProvider;
import com.gotham.command.entity.User;

class JwtServiceTest {

    private JwtService jwtService;
    private JwtProperties jwtProperties;

    @BeforeEach
    void setUp() {
        jwtProperties = new JwtProperties();
        jwtProperties.setSecret("gotham-command-test-secret-key-that-is-at-least-256-bits-long!");
        jwtProperties.setExpiration(900000L); // 15 minutes
        jwtService = new JwtService(jwtProperties);
    }

    @Test
    @DisplayName("Should generate valid JWT with user ID, email, and roles")
    void testGenerateAccessToken() {
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        user.setEmail("batman@gotham.city");
        user.setFullName("Bruce Wayne");
        user.setProvider(AuthProvider.GOOGLE);

        String token = jwtService.generateAccessToken(user);

        assertThat(token).isNotBlank();
        assertThat(jwtService.isTokenValid(token)).isTrue();
        assertThat(jwtService.extractUserId(token)).isEqualTo(userId);
        assertThat(jwtService.extractEmail(token)).isEqualTo("batman@gotham.city");
    }

    @Test
    @DisplayName("Should reject invalid or tampered JWT")
    void testTamperedTokenRejection() {
        String token = jwtService.generateToken("test-subject");
        String tampered = token + "xyz";

        assertThat(jwtService.isTokenValid(tampered)).isFalse();
    }

    @Test
    @DisplayName("Should reject expired JWT")
    void testExpiredTokenRejection() {
        jwtProperties.setExpiration(-1000L); // Expired 1 second ago
        JwtService expiredJwtService = new JwtService(jwtProperties);

        String token = expiredJwtService.generateToken("test-subject");

        assertThat(jwtService.isTokenValid(token)).isFalse();
    }

    @Test
    @DisplayName("Should reject malformed JWT tokens gracefully")
    void testMalformedTokenRejection() {
        assertThat(jwtService.isTokenValid("not.a.valid.jwt")).isFalse();
        assertThat(jwtService.isTokenValid("")).isFalse();
        assertThat(jwtService.isTokenValid(null)).isFalse();
    }
}
