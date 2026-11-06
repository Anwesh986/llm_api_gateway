package com.gateway.api.controller;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.gateway.api.dto.ChatRequest;
import com.gateway.api.service.FallbackRoutingEngine;

import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import org.springframework.http.codec.ServerSentEvent;

class ChatStreamControllerTest {

    @Test
    void emitsHeartbeatCommentEveryFifteenSeconds() {
        ChatRequest request = new ChatRequest(
                "auto-fast",
                List.of(new ChatRequest.Message("user", "Hello")),
                0.7,
                1000
        );

        FallbackRoutingEngine engine =
                new FallbackRoutingEngine(
                        ignored -> Flux.just("token"),
                        ignored -> Flux.empty()
                );

        ChatStreamController controller =
                new ChatStreamController(engine);

        StepVerifier.withVirtualTime(() ->
                        controller.streamCompletion(request))
                .expectNextMatches(event ->
                        "message".equals(event.event())
                                && "token".equals(event.data()))
                .thenAwait(Duration.ofSeconds(15))
                .expectNextMatches(event ->
                        "keep-alive".equals(event.comment())
                                && event.data() == null)
                .thenCancel()
                .verify();
    }
}
