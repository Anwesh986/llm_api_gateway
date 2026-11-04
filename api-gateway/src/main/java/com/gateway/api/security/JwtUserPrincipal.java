package com.gateway.api.security;

public record JwtUserPrincipal(
        String userId,
        String tier
) {
}