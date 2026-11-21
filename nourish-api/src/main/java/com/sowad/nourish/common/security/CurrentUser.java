package com.sowad.nourish.common.security;

import java.util.Set;

public record CurrentUser(
        String id, String username, String displayName, String role, Set<String> roles) {
    public CurrentUser {
        roles = Set.copyOf(roles);
    }
}
