package com.sowad.nourish.purchase.command;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record CreatePurchase(
    String supplierId, Instant purchasedAt, String reference, String notes, List<Line> items) {
  public record Line(
      String productId,
      BigDecimal quantity,
      BigDecimal unitPrice,
      String pricingBasis,
      List<Bag> packages,
      List<MedicinePack> medicinePacks,
      Measure measurement,
      Pricing purchasePricing) {}

  public record Bag(BigDecimal bagSizeKg, Long bagCount) {}

  public record MedicinePack(BigDecimal amount, String unitCode, Long packCount) {}

  public record Measure(BigDecimal amount, String unitCode) {}

  public record Pricing(
      BigDecimal unitPurchaseRate,
      BigDecimal unitSellingRate,
      Boolean setGlobalSellingRate,
      List<Price> packagePrices) {}

  public record Price(
      String key,
      BigDecimal purchasePricePerPackage,
      BigDecimal suggestedSalePricePerPackage,
      BigDecimal purchaseRatePerUnit,
      String purchaseRateUnit,
      BigDecimal suggestedSaleRatePerUnit,
      String suggestedSaleRateUnit) {}
}
