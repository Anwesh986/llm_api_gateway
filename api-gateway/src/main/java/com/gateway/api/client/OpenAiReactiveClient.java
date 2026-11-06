package com.gateway.api.client;

import java.time.Duration;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.gateway.api.dto.ChatRequest;

import org.springframework.beans.factory.annotation.Value;
import reactor.core.publisher.Flux;

@Component("primaryProvider")
public class OpenAiReactiveClient implements ReactiveProviderClient {
    private static final Duration CHUNK_DELAY = Duration.ofMillis(50);

    private final boolean simulateRateLimit;

    public OpenAiReactiveClient(
        @Value ("${providers.openai.simulate-rate-limit:false}")
        boolean simulateRateLimit
    ){
        this.simulateRateLimit = simulateRateLimit;
    }

    public Flux<String> streamCompletion(ChatRequest request){
        List<String> chunks = List.of(
            "This is a simulated ",
            "streaming response ",
            "from the OpenAI provider. ");

        Flux<String> response = Flux.fromIterable(chunks).delayElements(CHUNK_DELAY);

        if(simulateRateLimit){
            return response.concatWith(
                Flux.error(new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Simulated OpenAI rate limit exceeded"
                ))
            );
        }
        return response;
    }

}
