package com.gotham.command.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.gotham.command.entity.AuthProvider;
import com.gotham.command.entity.RefreshToken;
import com.gotham.command.entity.User;
import com.gotham.command.repository.RefreshTokenRepository;
import com.gotham.command.repository.UserRepository;
import com.gotham.command.security.JwtProperties;
import com.gotham.command.security.JwtService;
import com.gotham.command.service.RefreshTokenService;
import com.gotham.command.service.UserService;

import jakarta.servlet.http.Cookie;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private UserService userService;

    private User testUser;
    private String validToken;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setEmail("alfred@wayne.corp");
        testUser.setFullName("Alfred Pennyworth");
        testUser.setDisplayName("Alfred Pennyworth");
        testUser.setUsername("alfred");
        testUser.setProvider(AuthProvider.GOOGLE);
        testUser.setProviderId("google-alfred-999");
        testUser.setPasswordHash("$2a$10$superSecretHash");
        testUser.setActive(true);
        testUser = userRepository.save(testUser);

        validToken = jwtService.generateAccessToken(testUser);
    }

    @Test
    @DisplayName("GET /api/auth/me should return 401 when Authorization header is missing")
    void testGetMeUnauthorizedWhenNoToken() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/auth/me should return 401 when token is tampered or invalid")
    void testGetMeUnauthorizedWhenTokenInvalid() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                .header("Authorization", "Bearer invalid.token.value"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/auth/me should return 200 with user profile and never expose passwordHash")
    void testGetMeSuccess() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                .header("Authorization", "Bearer " + validToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(testUser.getId().toString()))
            .andExpect(jsonPath("$.email").value("alfred@wayne.corp"))
            .andExpect(jsonPath("$.fullName").value("Alfred Pennyworth"))
            .andExpect(jsonPath("$.provider").value("GOOGLE"))
            .andExpect(jsonPath("$.passwordHash").doesNotExist())
            .andExpect(jsonPath("$.password_hash").doesNotExist());
    }

    @Test
    @DisplayName("POST /api/auth/refresh should rotate token and return new access token and Set-Cookie")
    void testRefreshTokenSuccess() throws Exception {
        RefreshToken refreshToken = refreshTokenService.generateAndSaveRefreshToken(testUser);

        mockMvc.perform(post("/api/auth/refresh")
                .cookie(new Cookie("gotham_refresh_token", refreshToken.getToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isNotEmpty())
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andExpect(header().exists(HttpHeaders.SET_COOKIE))
            .andExpect(cookie().httpOnly("gotham_refresh_token", true));

        // Old token should now be revoked
        RefreshToken oldReloaded = refreshTokenRepository.findByToken(refreshToken.getToken()).orElseThrow();
        assertThat(oldReloaded.isRevoked()).isTrue();
    }

    @Test
    @DisplayName("POST /api/auth/refresh should reject revoked refresh token with 401")
    void testRefreshWithRevokedTokenFails() throws Exception {
        RefreshToken refreshToken = refreshTokenService.generateAndSaveRefreshToken(testUser);
        refreshTokenService.revokeToken(refreshToken.getToken());

        mockMvc.perform(post("/api/auth/refresh")
                .cookie(new Cookie("gotham_refresh_token", refreshToken.getToken())))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").isNotEmpty());
    }

    @Test
    @DisplayName("POST /api/auth/logout should revoke token and clear cookie")
    void testLogoutSuccess() throws Exception {
        RefreshToken refreshToken = refreshTokenService.generateAndSaveRefreshToken(testUser);

        mockMvc.perform(post("/api/auth/logout")
                .cookie(new Cookie("gotham_refresh_token", refreshToken.getToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("Logged out successfully"))
            .andExpect(cookie().maxAge("gotham_refresh_token", 0));

        RefreshToken reloaded = refreshTokenRepository.findByToken(refreshToken.getToken()).orElseThrow();
        assertThat(reloaded.isRevoked()).isTrue();
    }

    @Test
    @DisplayName("UserService: Should create new Google OAuth user when identity does not exist")
    void testGoogleOAuthUserCreation() {
        User googleUser = userService.processGoogleOAuthUser(
            "google-sub-12345",
            "barbara.gordon@gcpd.gov",
            "Barbara Gordon",
            "https://avatar.gotham/barbara.png"
        );

        assertThat(googleUser.getId()).isNotNull();
        assertThat(googleUser.getProvider()).isEqualTo(AuthProvider.GOOGLE);
        assertThat(googleUser.getProviderId()).isEqualTo("google-sub-12345");
        assertThat(googleUser.getEmail()).isEqualTo("barbara.gordon@gcpd.gov");
        assertThat(googleUser.getFullName()).isEqualTo("Barbara Gordon");
        assertThat(googleUser.getAvatarUrl()).isEqualTo("https://avatar.gotham/barbara.png");
        assertThat(googleUser.getPasswordHash()).isNull();

        // Repeated login with same Google sub should return existing user and not create duplicate
        User secondLogin = userService.processGoogleOAuthUser(
            "google-sub-12345",
            "barbara.gordon@gcpd.gov",
            "Barbara Gordon",
            "https://avatar.gotham/barbara_updated.png"
        );
        assertThat(secondLogin.getId()).isEqualTo(googleUser.getId());
        assertThat(secondLogin.getAvatarUrl()).isEqualTo("https://avatar.gotham/barbara_updated.png");
    }

    @Test
    @DisplayName("UserService: Should reject automatic account takeover when email collision occurs with LOCAL provider")
    void testRejectAccountTakeoverOnProviderMismatch() {
        User localUser = new User();
        localUser.setEmail("selina@kyle.cat");
        localUser.setFullName("Selina Kyle");
        localUser.setProvider(AuthProvider.LOCAL);
        localUser.setProviderId("local-selina");
        localUser.setPasswordHash("hashed_local_password");
        userRepository.save(localUser);

        // Attempting to log in with Google using the same email address must be rejected
        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
            userService.processGoogleOAuthUser(
                "google-attacker-sub",
                "selina@kyle.cat",
                "Selina Kyle",
                null
            )
        );

        assertThat(exception.getMessage()).contains("Automatic account linking is not supported");
    }
}
