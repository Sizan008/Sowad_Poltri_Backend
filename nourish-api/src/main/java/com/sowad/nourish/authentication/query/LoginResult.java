package com.sowad.nourish.authentication.query;

import com.sowad.nourish.common.security.CurrentUser;

import java.time.Instant;

public record LoginResult(
        String accessToken, String tokenType, Instant expiresAt, CurrentUser user) {
    @Override
    public String toString() {
        return "LoginResult[token redacted]";
    }
}
