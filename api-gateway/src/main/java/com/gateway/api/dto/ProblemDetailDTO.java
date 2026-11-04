package com.gateway.api.dto;

import java.time.Instant;

public record ProblemDetailDTO(
        String type,
        String title,
        int status,
        String detail,
        String instance,
        Instant timestamp
) {
}