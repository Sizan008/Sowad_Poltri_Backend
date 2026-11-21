package com.sowad.nourish.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@org.springframework.core.annotation.Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE)
@Component
public class RequestLoggingFilter extends OncePerRequestFilter {
    private static final Logger LOGGER = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = UUID.randomUUID().toString();
        long start = System.nanoTime();
        MDC.put("requestId", requestId);
        response.setHeader("X-Request-Id", requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            // No request body, credentials, headers or query parameters in logs.
            LOGGER.info(
                    "method={} status={} durationMs={}",
                    request.getMethod(),
                    response.getStatus(),
                    (System.nanoTime() - start) / 1_000_000);
            MDC.remove("requestId");
        }
    }
}
