package com.sowad.nourish.payment.command;

import java.math.BigDecimal;
import java.time.Instant;

public record CollectPayment(
    String invoiceId,
    BigDecimal amount,
    Instant paidAt,
    String method,
    String receivingAccountId,
    String paymentProvider,
    String senderNumber,
    String transactionRef,
    String note) {}
