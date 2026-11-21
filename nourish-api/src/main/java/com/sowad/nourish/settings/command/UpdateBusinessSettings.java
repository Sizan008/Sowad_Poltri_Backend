package com.sowad.nourish.settings.command;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record UpdateBusinessSettings(
        @NotBlank @Size(max = 200) String businessName,
        @NotNull @Size(max = 120) String ownerName,
        @NotNull @Size(max = 40) String phone,
        @NotNull @Email @Size(max = 200) String email,
        @NotNull @Size(max = 1000) String address,
        @NotNull @Size(max = 500) String invoiceFooter,
        @NotNull @DecimalMin("0") @DecimalMax("1000000") @Digits(integer = 7, fraction = 3)
                BigDecimal lowStockDefault,
        @NotBlank @Pattern(regexp = "bn|en") String uiLanguage) {}
