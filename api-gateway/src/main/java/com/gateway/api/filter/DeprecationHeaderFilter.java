package com.gateway.api.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class DeprecationHeaderFilter extends OncePerRequestFilter {

    private static final String API_VERSION_HEADER = "X-API-VERSION";
    private static final String V1_PATH_PREFIX = "/api/v1/";
    private static final String DEPRECATION = "@1767225599";
    private static final String SUNSET = "Thu, 31 Dec 2026 23:59:59 GMT";
    private static final String SUCCESSOR_LINK =
            "</api/v2/chat/completions>; rel=\"successor-version\"";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        if (isDeprecatedV1Request(request)) {
            response.setHeader("Deprecation", DEPRECATION);
            response.setHeader("Sunset", SUNSET);
            response.setHeader("Link", SUCCESSOR_LINK);
        }

        filterChain.doFilter(request, response);
    }

    private boolean isDeprecatedV1Request(HttpServletRequest request) {
        return request.getRequestURI().startsWith(V1_PATH_PREFIX)
                && !"2".equals(request.getHeader(API_VERSION_HEADER));
    }
}
