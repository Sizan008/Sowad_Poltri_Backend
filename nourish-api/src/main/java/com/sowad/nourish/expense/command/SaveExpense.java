package com.sowad.nourish.expense.command;

import java.math.BigDecimal;
import java.time.Instant;

public record SaveExpense(
    String title,
    String category,
    String method,
    BigDecimal amount,
    Instant spentAt,
    String reference,
    String note,
    Long expectedRevision) {}
