package com.gateway.api.client;

import com.gateway.api.dto.ChatRequest;

import reactor.core.publisher.Flux;

public interface ReactiveProviderClient {
    Flux<String> streamCompletion(ChatRequest request);
}
