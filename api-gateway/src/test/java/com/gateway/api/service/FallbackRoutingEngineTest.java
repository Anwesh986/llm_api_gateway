package com.gateway.api.service;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.gateway.api.client.ReactiveProviderClient;
import com.gateway.api.dto.ChatRequest;

import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

public class FallbackRoutingEngineTest {

    private final ChatRequest request = new ChatRequest(
        "auto-fast",
        List.of(new ChatRequest.Message(
            "user",
            "Hello, how are you?"
        )),
        0.7,
        1000
    );

    @Test 
    void returnsPrimaryProviderChunksWhenPrimarySucceeds(){
        ReactiveProviderClient primary = request ->
                Flux.just("primary-1", "primary-2");

        ReactiveProviderClient fallback = request ->
                Flux.just("fallback-1", "fallback-2");

        FallbackRoutingEngine engine =
                new FallbackRoutingEngine(primary, fallback);

        StepVerifier.create(engine.streamCompletion(request))
                .expectNext("primary-1")
                .expectNext("primary-2")
                .verifyComplete();
    }

    @Test
    void switchesToFallbackWhenPrimaryFails() {
        ReactiveProviderClient primary = request ->
                Flux.error(new RuntimeException("Primary provider failed"));

        ReactiveProviderClient fallback = request ->
                Flux.just("fallback-1", "fallback-2");

        FallbackRoutingEngine engine =
                new FallbackRoutingEngine(primary, fallback);

        StepVerifier.create(engine.streamCompletion(request))
                .expectNext("fallback-1")
                .expectNext("fallback-2")
                .verifyComplete();
    }
    
}
