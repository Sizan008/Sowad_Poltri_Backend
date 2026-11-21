package com.sowad.nourish.authentication.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record Login(
        @NotBlank @Size(max = 64) String username, @NotBlank @Size(max = 72) String password) {
    @Override
    public String toString() {
        return "Login[credentials redacted]";
    }
}
