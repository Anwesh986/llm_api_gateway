package com.gateway.api.dto;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn) {
}
