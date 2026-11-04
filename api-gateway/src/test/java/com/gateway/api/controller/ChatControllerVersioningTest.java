package com.gateway.api.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.handler;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChatController.class)
@AutoConfigureMockMvc(addFilters = false)
class ChatControllerVersioningTest {

    @Autowired
    private MockMvc mockMvc;

    private static final String REQUEST_BODY = """
            {
              "model": "gpt-4o",
              "messages": [
                {
                  "role": "user",
                  "content": "Hello"
                }
              ]
            }
            """;

    @Test
    void requestWithoutVersionHeaderUsesV1Handler() throws Exception {
        mockMvc.perform(post("/api/v1/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_BODY))
                .andExpect(status().isOk())
                .andExpect(handler().methodName("completions"));
    }

    @Test
    void versionTwoHeaderUsesV2Handler() throws Exception {
        mockMvc.perform(post("/api/v1/chat/completions")
                        .header("X-API-VERSION", "2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_BODY))
                .andExpect(status().isOk())
                .andExpect(handler().methodName("completionsV2"));
    }
}
