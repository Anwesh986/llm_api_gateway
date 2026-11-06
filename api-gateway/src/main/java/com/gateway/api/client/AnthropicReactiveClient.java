package com.gateway.api.client;

import java.time.Duration;
import java.util.List;

import org.springframework.stereotype.Component;

import com.gateway.api.dto.ChatRequest;

import reactor.core.publisher.Flux;

@Component("fallbackProvider")
public class AnthropicReactiveClient implements ReactiveProviderClient {

    private static final Duration CHUNK_DELAY = Duration.ofMillis(50);

    @Override
    public Flux<String> streamCompletion(ChatRequest request) {
        List<String> chunks = List.of(
                "This is a simulated ",
                "streaming response ",
                "from the Anthropic provider. ");

        return Flux.fromIterable(chunks)
                .delayElements(CHUNK_DELAY);
    }
}
