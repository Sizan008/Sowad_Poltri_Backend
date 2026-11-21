package com.sowad.nourish.catalog.command;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record SavePackaging(
    @NotNull @DecimalMin("0.001") @DecimalMax("1000000") @Digits(integer = 7, fraction = 3)
        BigDecimal amount,
    @NotBlank @Pattern(regexp = "kg|g|ml|l|piece") String unitCode) {}
