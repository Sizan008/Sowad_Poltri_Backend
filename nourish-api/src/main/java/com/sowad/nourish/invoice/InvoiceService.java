package com.sowad.nourish.invoice;

public interface InvoiceService {
  java.util.Map<String, Object> create(
      String key, com.sowad.nourish.invoice.command.CreateInvoice command);
}
