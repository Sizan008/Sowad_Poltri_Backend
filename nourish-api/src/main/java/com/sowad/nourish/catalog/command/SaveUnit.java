package com.sowad.nourish.catalog.command;

import jakarta.validation.constraints.*;

public record SaveUnit(
    @Size(max = 64) String id,
    @NotBlank @Pattern(regexp = "[a-z][a-z0-9_-]{0,19}") String code,
    @NotBlank @Size(max = 80) String name,
    @NotBlank @Size(max = 20) String symbol,
    @NotNull Boolean allowDecimal) {}
