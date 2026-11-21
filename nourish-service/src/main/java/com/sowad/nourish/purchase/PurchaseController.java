package com.sowad.nourish.purchase;

import com.sowad.nourish.common.masterdata.StrictCommandDecoder;
import com.sowad.nourish.purchase.command.CreatePurchase;
import jakarta.validation.constraints.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
public class PurchaseController {
  private final PurchaseService service;
  private final PurchaseQueries queries;
  private final StrictCommandDecoder decoder;

  public PurchaseController(
      PurchaseService service, PurchaseQueries queries, StrictCommandDecoder decoder) {
    this.service = service;
    this.queries = queries;
    this.decoder = decoder;
  }

  @PostMapping("/transactions/purchases")
  @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
  public Object create(@RequestHeader("Idempotency-Key") String key, @RequestBody String body) {
    return service.create(key, decoder.decode(body, CreatePurchase.class));
  }

  @GetMapping("/purchases")
  public Object list(
      @RequestParam(required = false) String supplierId,
      @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit,
      @RequestParam(defaultValue = "0") @Min(0) int offset) {
    return queries.list(supplierId, limit, offset);
  }

  @GetMapping("/purchases/{id}")
  public Object retrieve(@PathVariable String id) {
    return queries.retrieve(id);
  }

  @GetMapping("/purchaseItems")
  public Object items(@RequestParam String purchaseId) {
    return queries.retrieve(purchaseId).get("items");
  }
}
