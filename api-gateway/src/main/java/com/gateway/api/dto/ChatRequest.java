package com.gateway.api.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

public record ChatRequest(
    @NotBlank 
    String model,

    @NotEmpty 
    @Valid 
    List<Message> messages,

    @DecimalMin("0.0")
    @DecimalMax("2.0")
    Double temperature,

    @JsonProperty("max_tokens")
    @Min(1)
    @Max(4096)
    Integer maxTokens
) {
    public record Message(
        @NotBlank 
        String role,

        @NotBlank 
        String content
    ){
        
    }
}
