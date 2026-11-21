package com.sowad.nourish.payment;

public interface PaymentService {
  java.util.Map<String, Object> create(
      String key, com.sowad.nourish.payment.command.CollectPayment command);
}
