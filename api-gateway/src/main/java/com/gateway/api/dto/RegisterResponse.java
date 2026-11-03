package com.gateway.api.dto;

import java.util.UUID;

public record RegisterResponse(
        UUID id,
        String email,
        String role,
        String tier) {
}
