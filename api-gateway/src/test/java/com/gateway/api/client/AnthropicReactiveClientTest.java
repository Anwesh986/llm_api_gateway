package com.gateway.api.client;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.gateway.api.dto.ChatRequest;

import reactor.test.StepVerifier;

class AnthropicReactiveClientTest {

    private final ChatRequest request = new ChatRequest(
            "auto-fast",
            List.of(new ChatRequest.Message("user", "Hello")),
            0.7,
            1000
    );

    @Test
    void emitsCompleteFallbackResponse() {
        AnthropicReactiveClient client = new AnthropicReactiveClient();

        StepVerifier.create(client.streamCompletion(request))
                .expectNext("This is a simulated ")
                .expectNext("streaming response ")
                .expectNext("from the Anthropic provider. ")
                .verifyComplete();
    }
}
