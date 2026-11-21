package com.sowad.nourish.purchase;

public interface PurchaseService {
  java.util.Map<String, Object> create(
      String requestKey, com.sowad.nourish.purchase.command.CreatePurchase command);
}
