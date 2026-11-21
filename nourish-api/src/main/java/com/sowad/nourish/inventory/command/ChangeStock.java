package com.sowad.nourish.inventory.command;

import java.math.BigDecimal;
import java.util.List;

public record ChangeStock(
    String productId,
    String mode,
    Long expectedRevision,
    BigDecimal quantity,
    List<PackageCount> packages,
    String reason) {
  public record PackageCount(String packagingId, Long count) {}
}
