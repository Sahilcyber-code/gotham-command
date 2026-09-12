package com.gotham.command.controller;

import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gotham.command.config.AuthCookieProperties;
import com.gotham.command.dto.auth.TokenResponse;
import com.gotham.command.dto.user.UserDto;
import com.gotham.command.entity.RefreshToken;
import com.gotham.command.entity.User;
import com.gotham.command.repository.UserRepository;
import com.gotham.command.security.JwtProperties;
import com.gotham.command.security.JwtService;
import com.gotham.command.security.SecurityUserPrincipal;
import com.gotham.command.service.RefreshTokenService;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final RefreshTokenService refreshTokenService;
    private final UserRepository userRepository;
    private final AuthCookieProperties authCookieProperties;

    public AuthController(
        JwtService jwtService,
        JwtProperties jwtProperties,
        RefreshTokenService refreshTokenService,
        UserRepository userRepository,
        AuthCookieProperties authCookieProperties
    ) {
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
        this.refreshTokenService = refreshTokenService;
        this.userRepository = userRepository;
        this.authCookieProperties = authCookieProperties;
    }

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(@AuthenticationPrincipal SecurityUserPrincipal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Unauthorized"));
        }

        User user = userRepository.findById(principal.getId()).orElse(principal.getUser());
        return ResponseEntity.ok(UserDto.fromEntity(user));
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(
        @CookieValue(name = "${app.auth.cookie.name:gotham_refresh_token}", required = false) String cookieToken,
        @RequestBody(required = false) Map<String, String> body
    ) {
        String tokenToRefresh = cookieToken;
        if ((tokenToRefresh == null || tokenToRefresh.isBlank()) && body != null) {
            tokenToRefresh = body.get("refreshToken");
        }

        if (tokenToRefresh == null || tokenToRefresh.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Refresh token is missing"));
        }

        Optional<RefreshToken> rotated = refreshTokenService.rotateRefreshToken(tokenToRefresh);
        if (rotated.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Invalid, expired, or revoked refresh token"));
        }

        RefreshToken newRefreshToken = rotated.get();
        String newAccessToken = jwtService.generateAccessToken(newRefreshToken.getUser());

        ResponseCookie cookie = ResponseCookie.from(authCookieProperties.getName(), newRefreshToken.getToken())
            .httpOnly(true)
            .secure(authCookieProperties.isSecure())
            .path(authCookieProperties.getPath())
            .maxAge(authCookieProperties.getMaxAgeSeconds())
            .sameSite(authCookieProperties.getSameSite())
            .build();

        TokenResponse tokenResponse = TokenResponse.of(
            newAccessToken,
            null,
            jwtProperties.getExpiration() / 1000,
            newRefreshToken.getExpiresAt()
        );

        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, cookie.toString())
            .body(tokenResponse);
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(
        @CookieValue(name = "${app.auth.cookie.name:gotham_refresh_token}", required = false) String cookieToken,
        @RequestBody(required = false) Map<String, String> body
    ) {
        String tokenToRevoke = cookieToken;
        if ((tokenToRevoke == null || tokenToRevoke.isBlank()) && body != null) {
            tokenToRevoke = body.get("refreshToken");
        }

        if (tokenToRevoke != null && !tokenToRevoke.isBlank()) {
            refreshTokenService.revokeToken(tokenToRevoke);
        }

        ResponseCookie clearCookie = ResponseCookie.from(authCookieProperties.getName(), "")
            .httpOnly(true)
            .secure(authCookieProperties.isSecure())
            .path(authCookieProperties.getPath())
            .maxAge(0)
            .sameSite(authCookieProperties.getSameSite())
            .build();

        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, clearCookie.toString())
            .body(Map.of("message", "Logged out successfully"));
    }
}
