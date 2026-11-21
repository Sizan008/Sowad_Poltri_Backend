package com.sowad.nourish.receivingaccount.command;

import jakarta.validation.constraints.*;

public record SaveReceivingAccount(
    @NotNull @Pattern(regexp = "mfs|bank") String kind,
    @NotBlank @Size(min = 2, max = 80) String label,
    @NotNull Boolean active,
    @Size(max = 10) String provider,
    @Size(max = 30) String walletNumber,
    @Size(max = 100) String bankName,
    @Size(max = 60) String accountNumber,
    @Size(max = 100) String accountName) {}
