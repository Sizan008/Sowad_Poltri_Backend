package com.sowad.nourish.user.command;

import jakarta.validation.constraints.*;

public record CreateUser(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9._-]{3,64}") String username,
        @NotBlank @Size(min = 12, max = 72) String password,
        @NotBlank @Size(max = 120) String displayName,
        @NotBlank @Pattern(regexp = "admin|operator|viewer") String role) {
    @Override
    public String toString() {
        return "CreateUser[credentials redacted]";
    }
}
