package com.sowad.nourish.customerrate.command;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;

/** Atomic replacement. An empty list explicitly clears the current rates. */
public record SaveCustomerRates(@NotNull @Size(max = 100) List<@NotNull @Valid Rate> rates) {
  public record Rate(
      @NotBlank @Size(max = 64) String productId,
      @Size(max = 64) String packagingId,
      @DecimalMin(value = "0", inclusive = false)
          @DecimalMax("1000000")
          @Digits(integer = 7, fraction = 6)
          BigDecimal pricePerUnit,
      @DecimalMin(value = "0", inclusive = false)
          @DecimalMax("1000000")
          @Digits(integer = 7, fraction = 6)
          BigDecimal pricePerKg,
      @DecimalMin(value = "0", inclusive = false)
          @DecimalMax("1000000000")
          @Digits(integer = 10, fraction = 2)
          BigDecimal pricePerPackage,
      @Size(max = 20) String unit) {}
}
