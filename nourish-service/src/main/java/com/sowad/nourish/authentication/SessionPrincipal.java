package com.sowad.nourish.authentication;

import com.sowad.nourish.common.security.CurrentUser;

public record SessionPrincipal(CurrentUser user, String tokenHash) {
    @Override
    public String toString() {
        return "SessionPrincipal[redacted]";
    }
}
