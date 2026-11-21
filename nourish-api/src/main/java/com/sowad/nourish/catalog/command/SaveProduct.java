package com.sowad.nourish.catalog.command;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record SaveProduct(
    @NotBlank @Size(max = 120) String name,
    @NotBlank @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_-]{1,29}") String sku,
    @NotBlank @Size(max = 64) String categoryId,
    @Size(max = 70) String subtype,
    @Size(max = 8) String grade,
    @Size(max = 120) String manufacturer,
    @Size(max = 20) String medicineForm,
    @Size(max = 100) String feedGroup,
    @Size(max = 70) String feedType,
    @DecimalMin("0") @DecimalMax("1000000") @Digits(integer = 7, fraction = 3)
        BigDecimal lowStockThreshold) {}
