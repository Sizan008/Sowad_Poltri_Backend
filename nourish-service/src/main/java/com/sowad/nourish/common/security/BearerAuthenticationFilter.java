package com.sowad.nourish.common.security;

import com.sowad.nourish.authentication.SessionAuthenticationService;

import jakarta.servlet.*;
import jakarta.servlet.http.*;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Registered only in SecurityFilterChain, never as a servlet component. */
public class BearerAuthenticationFilter extends OncePerRequestFilter {
    private final SessionAuthenticationService sessions;
    private final SecurityErrorWriter errors;

    public BearerAuthenticationFilter(
            SessionAuthenticationService sessions, SecurityErrorWriter errors) {
        this.sessions = sessions;
        this.errors = errors;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null) {
            if (!header.startsWith("Bearer ") || header.length() != 50) {
                errors.write(response, 401, "UNAUTHORIZED", "Invalid or expired session.");
                return;
            }
            java.util.Optional<com.sowad.nourish.authentication.SessionPrincipal> found;
            try {
                found = sessions.authenticate(header.substring(7));
            } catch (RuntimeException error) {
                errors.write(
                        response,
                        503,
                        "DATABASE_UNAVAILABLE",
                        "Authentication service unavailable.");
                return;
            }
            if (found.isEmpty()) {
                errors.write(response, 401, "UNAUTHORIZED", "Invalid or expired session.");
                return;
            }
            var principal = found.get();
            var authorities =
                    principal.user().roles().stream()
                            .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                            .toList();
            var context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(
                    new UsernamePasswordAuthenticationToken(principal, null, authorities));
            SecurityContextHolder.setContext(context);
        }
        chain.doFilter(request, response);
    }
}
