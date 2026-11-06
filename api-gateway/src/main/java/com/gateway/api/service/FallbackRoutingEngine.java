package com.gateway.api.service;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Qualifier;

import com.gateway.api.client.ReactiveProviderClient;
import com.gateway.api.dto.ChatRequest;

import reactor.core.publisher.Flux;

@Service 
public class FallbackRoutingEngine {
    private final ReactiveProviderClient primaryProvider;
    private final ReactiveProviderClient fallbackProvider;

    public FallbackRoutingEngine(
        @Qualifier("primaryProvider") ReactiveProviderClient primaryProvider,
        @Qualifier("fallbackProvider") ReactiveProviderClient fallbackProvider
    ){
        this.primaryProvider = primaryProvider;
        this.fallbackProvider = fallbackProvider;
    }

    public Flux<String> streamCompletion(ChatRequest request){
        return primaryProvider.streamCompletion(request)
            .onErrorResume(throwable -> {
                return fallbackProvider.streamCompletion(request);
            });
    }
    
    
}
