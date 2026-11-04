package com.gateway.api.exception;

import com.gateway.api.controller.ChatController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChatController.class)
@AutoConfigureMockMvc(addFilters = false)
class GatewayExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void invalidChatRequestReturnsProblemDetails() throws Exception {
        mockMvc.perform(post("/api/v1/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "model": "",
                                  "messages": []
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type")
                        .value("https://api.gateway.io/errors/validation-failed"))
                .andExpect(jsonPath("$.title")
                        .value("Bad Request"))
                .andExpect(jsonPath("$.status")
                        .value(400))
                .andExpect(jsonPath("$.instance")
                        .value("/api/v1/chat/completions"))
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").exists());
    }
}
