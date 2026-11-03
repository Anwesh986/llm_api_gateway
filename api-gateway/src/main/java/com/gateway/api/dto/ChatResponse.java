package com.gateway.api.dto;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ChatResponse(
    String id,
    String provider,
    String model,

    boolean fallbackTriggered,

    List<Choice> choices,
    Usuage usuage
) {
    public record Choice(
        int index,
        Message message,

        @JsonProperty("finish_reason")
        String finishReason
    ){
    }

    public record Message(
        String role,
        String content
    ){
        
    }

    public record Usuage(
        @JsonProperty("prompt_tokens")
        int promptTokens,

        @JsonProperty("completion_tokens")
        int completionTokens,

        @JsonProperty("total_tokens")
        int totalTokens,

        @JsonProperty("cost_usd")
        BigDecimal costusd
    ){
        
    }
   
}
