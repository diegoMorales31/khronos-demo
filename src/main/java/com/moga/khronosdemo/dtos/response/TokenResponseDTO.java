package com.moga.khronosdemo.dtos.response;

import java.time.Instant;

public record TokenResponseDTO(
        String accessToken,
        String tokenType,
        Instant expiresAt,
        long expiresIn,
        // Swapped at POST /api/auth/refresh for a new access token and a new refresh token
        String refreshToken
) {

    public TokenResponseDTO withRefreshToken(String refreshToken) {
        return new TokenResponseDTO(accessToken, tokenType, expiresAt, expiresIn, refreshToken);
    }
}
