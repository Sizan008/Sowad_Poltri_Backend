package com.sowad.nourish.common.security;

import com.sowad.nourish.authentication.SessionPrincipal;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class SecurityContextCurrentUserProvider implements CurrentUserProvider {
    public SessionPrincipal getSession() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !(authentication.getPrincipal() instanceof SessionPrincipal session)) {
            throw new AuthenticationCredentialsNotFoundException("Authentication required");
        }
        return session;
    }

    @Override
    public CurrentUser getCurrentUser() {
        return getSession().user();
    }
}
