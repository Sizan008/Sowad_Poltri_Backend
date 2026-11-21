package com.sowad.nourish.invoice;

import com.sowad.nourish.common.masterdata.StrictCommandDecoder;
import com.sowad.nourish.invoice.command.CreateInvoice;
import com.sowad.nourish.payment.PaymentService;
import com.sowad.nourish.payment.command.CollectPayment;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
public class InvoiceController {
  private final InvoiceService service;
  private final InvoiceQueries queries;
  private final PaymentService payments;
  private final StrictCommandDecoder decoder;

  public InvoiceController(
      InvoiceService service,
      InvoiceQueries queries,
      PaymentService payments,
      StrictCommandDecoder decoder) {
    this.service = service;
    this.queries = queries;
    this.payments = payments;
    this.decoder = decoder;
  }

  @PostMapping("/transactions/sales")
  @ResponseStatus(HttpStatus.CREATED)
  public Object create(@RequestHeader("Idempotency-Key") String key, @RequestBody String body) {
    return service.create(key, decoder.decode(body, CreateInvoice.class));
  }

  @PostMapping("/transactions/payments")
  @ResponseStatus(HttpStatus.CREATED)
  public Object payment(@RequestHeader("Idempotency-Key") String key, @RequestBody String body) {
    return payments.create(key, decoder.decode(body, CollectPayment.class));
  }

  @GetMapping("/invoices")
  public Object list(
      @RequestParam(required = false) String customerId,
      @RequestParam(required = false) @Pattern(regexp = "unpaid|partial|paid") String status,
      @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit,
      @RequestParam(defaultValue = "0") @Min(0) int offset) {
    return queries.list(customerId, status, limit, offset);
  }

  @GetMapping({"/invoices/{id}", "/invoices/{id}/details"})
  public Object retrieve(@PathVariable String id) {
    return queries.retrieve(id);
  }

  @GetMapping("/invoices/{id}/download")
  public ResponseEntity<Object> download(@PathVariable String id) {
    var data = queries.retrieve(id);
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_JSON)
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"" + data.get("invoiceNumber") + ".json\"")
        .body(data);
  }

  @GetMapping("/invoiceItems")
  public Object items(@RequestParam String invoiceId) {
    return queries.retrieve(invoiceId).get("items");
  }

  @GetMapping("/payments")
  public Object payments(
      @RequestParam(required = false) String invoiceId,
      @RequestParam(required = false) String customerId,
      @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit,
      @RequestParam(defaultValue = "0") @Min(0) int offset) {
    return queries.payments(invoiceId, customerId, limit, offset);
  }

  @GetMapping("/payments/{id}")
  public Object payment(@PathVariable String id) {
    return queries.payment(id);
  }
}
