package com.gateway.api.dto;

public record AuthRequest(
        String email,
        String password) {
}
