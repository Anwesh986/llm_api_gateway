package com.gateway.api.filter;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

class DeprecationHeaderFilterTest {

    private final DeprecationHeaderFilter filter =
            new DeprecationHeaderFilter();

    @Test
    void v1RequestReceivesDeprecationHeaders() throws Exception {
        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/api/v1/chat/completions");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertEquals("@1767225599", response.getHeader("Deprecation"));
        assertEquals(
                "Thu, 31 Dec 2026 23:59:59 GMT",
                response.getHeader("Sunset"));
        assertEquals(
                "</api/v2/chat/completions>; rel=\"successor-version\"",
                response.getHeader("Link"));
    }

    @Test
    void versionTwoRequestDoesNotReceiveDeprecationHeaders() throws Exception {
        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/api/v1/chat/completions");
        request.addHeader("X-API-VERSION", "2");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertNull(response.getHeader("Deprecation"));
        assertNull(response.getHeader("Sunset"));
        assertNull(response.getHeader("Link"));
    }
}
