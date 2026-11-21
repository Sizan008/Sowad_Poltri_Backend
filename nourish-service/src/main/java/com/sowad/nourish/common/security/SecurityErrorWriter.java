package com.sowad.nourish.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sowad.nourish.common.response.ApiError;

import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

@Component
public class SecurityErrorWriter {
    private final ObjectMapper mapper;

    public SecurityErrorWriter(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public void write(HttpServletResponse response, int status, String code, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setHeader("Cache-Control", "no-store");
        if (status == 401) response.setHeader("WWW-Authenticate", "Bearer");
        mapper.writeValue(
                response.getOutputStream(),
                new ApiError(code, message, Map.of(), MDC.get("requestId"), Instant.now()));
    }
}
