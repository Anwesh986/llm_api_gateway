package com.gateway.api.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gateway.api.dto.ChatRequest;
import com.gateway.api.dto.ChatResponse;

import jakarta.validation.Valid;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@RestController 
@RequestMapping(
    path = "/api/v1/chat",
    produces = MediaType.APPLICATION_JSON_VALUE
)
public class ChatController {
    
    @PostMapping(
        path = "/completions",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        headers = "!X-API-VERSION"
    )
    public ResponseEntity<ChatResponse> completions(
        @Valid @RequestBody ChatRequest request){
        return createCompletionResponse(request);
    }

    @PostMapping(
        path = "/completions",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        headers = "X-API-VERSION=2"
    )
    public ResponseEntity<ChatResponse> completionsV2(
        @Valid @RequestBody ChatRequest request){
        return createCompletionResponse(request);
    }

    private ResponseEntity<ChatResponse> createCompletionResponse(
        ChatRequest request) {
        int promptTokens = estimatePromptTokens(request);
        int completionTokens = 3;

        ChatResponse response = new ChatResponse(
            "chatcmpl-"+UUID.randomUUID(),
            "OPENAI",
            request.model(),
            false,
            List.of(new ChatResponse.Choice(
                0,
                new ChatResponse.Message(
                    "assistant",
                     "Chat completion received."),"stop")),
                     new ChatResponse.Usuage(
                        promptTokens,
                        completionTokens,
                        promptTokens + completionTokens,
                        BigDecimal.ZERO
                     ));
                     return ResponseEntity.ok(response);
    }

    private int estimatePromptTokens(ChatRequest request){
        return request.messages().stream().mapToInt(message -> message.content().length()).sum();
    }
}
