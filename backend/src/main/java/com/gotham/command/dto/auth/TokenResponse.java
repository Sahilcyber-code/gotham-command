package com.gotham.command.dto.auth;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TokenResponse(
    String accessToken,
    String refreshToken,
    String tokenType,
    long expiresIn,
    Instant expiresAt
) {
    public static TokenResponse of(String accessToken, String refreshToken, long expiresIn, Instant expiresAt) {
        return new TokenResponse(accessToken, refreshToken, "Bearer", expiresIn, expiresAt);
    }
}
