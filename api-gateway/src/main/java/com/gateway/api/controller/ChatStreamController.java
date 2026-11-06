package com.gateway.api.controller;

import java.time.Duration;

import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gateway.api.dto.ChatRequest;
import com.gateway.api.service.FallbackRoutingEngine;

import reactor.core.publisher.Flux;

@RestController
@RequestMapping(path = "/api/v1/chat")
public class ChatStreamController {
    
    private final FallbackRoutingEngine fallbackRoutingEngine;

    public ChatStreamController(FallbackRoutingEngine fallbackRoutingEngine){
        this.fallbackRoutingEngine = fallbackRoutingEngine;
    }

    @GetMapping(
        path = "/stream",
        produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )public Flux<ServerSentEvent<String>> streamCompletion(ChatRequest request){
        Flux<ServerSentEvent<String>> tokenEvents =
                fallbackRoutingEngine.streamCompletion(request)
                        .map(chunk -> ServerSentEvent.<String>builder()
                                .event("message")
                                .data(chunk)
                                .build());

        Flux<ServerSentEvent<String>> heartbeatEvents =
                Flux.interval(Duration.ofSeconds(15))
                        .map(sequence -> ServerSentEvent.<String>builder()
                                .comment("keep-alive")
                                .build());

        return Flux.merge(tokenEvents, heartbeatEvents);
    }    
}