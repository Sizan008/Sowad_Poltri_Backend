package com.sowad.nourish.invoice.command;

import com.sowad.nourish.purchase.command.CreatePurchase.Bag;
import com.sowad.nourish.purchase.command.CreatePurchase.Measure;
import com.sowad.nourish.purchase.command.CreatePurchase.MedicinePack;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record CreateInvoice(
    String customerId,
    Instant issuedAt,
    Instant dueDate,
    BigDecimal discountAmount,
    BigDecimal initialPaid,
    String paymentMethod,
    String receivingAccountId,
    String paymentProvider,
    String senderNumber,
    String transactionRef,
    String notes,
    String lastNote,
    List<Line> items) {
  public record Line(
      String productId,
      BigDecimal quantity,
      BigDecimal unitPrice,
      String pricingBasis,
      List<Bag> packages,
      List<MedicinePack> medicinePacks,
      Measure measurement,
      String rateMode,
      BigDecimal manualLineTotal) {
    public Line(String productId, BigDecimal quantity, BigDecimal unitPrice, String pricingBasis,
        List<Bag> packages, List<MedicinePack> medicinePacks, Measure measurement) {
      this(productId, quantity, unitPrice, pricingBasis, packages, medicinePacks, measurement, null, null);
    }
  }
}
