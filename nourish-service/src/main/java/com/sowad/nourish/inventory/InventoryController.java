package com.sowad.nourish.inventory;

import com.sowad.nourish.common.masterdata.StrictCommandDecoder;
import com.sowad.nourish.inventory.command.ChangeStock;
import jakarta.validation.constraints.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
public class InventoryController {
  private final InventoryService service;
  private final InventoryQueries queries;
  private final StrictCommandDecoder decoder;

  public InventoryController(
      InventoryService service, InventoryQueries queries, StrictCommandDecoder decoder) {
    this.service = service;
    this.queries = queries;
    this.decoder = decoder;
  }

  @PostMapping("/transactions/stock-adjustments")
  @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
  public Object change(@RequestHeader("Idempotency-Key") String key, @RequestBody String body) {
    return service.change(key, decoder.decode(body, ChangeStock.class));
  }

  @GetMapping("/inventory")
  public Object list(
      @RequestParam(required = false) String productId,
      @RequestParam(required = false) @Pattern(regexp = "feed|medicine|chick|fish") String kind,
      @RequestParam(defaultValue = "false") boolean lowStock,
      @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit,
      @RequestParam(defaultValue = "0") @Min(0) int offset) {
    return queries.list(productId, kind, lowStock, limit, offset);
  }

  @GetMapping("/inventory/{productId}")
  public Object retrieve(@PathVariable String productId) {
    return queries.retrieve(productId);
  }

  @GetMapping("/packageStocks")
  public Object bags(
      @RequestParam(required = false) String productId,
      @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit,
      @RequestParam(defaultValue = "0") @Min(0) int offset) {
    return queries.packages(productId, "feed", limit, offset);
  }

  @GetMapping("/medicinePacks")
  public Object medicine(
      @RequestParam(required = false) String productId,
      @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit,
      @RequestParam(defaultValue = "0") @Min(0) int offset) {
    return queries.packages(productId, "medicine", limit, offset);
  }

  @GetMapping("/stockMovements")
  public Object movements(
      @RequestParam(required = false) String productId,
      @RequestParam(required = false) @Pattern(regexp = "purchase|sale|adjustment|recount")
          String type,
      @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit,
      @RequestParam(defaultValue = "0") @Min(0) int offset) {
    return queries.movements(productId, type, limit, offset);
  }

  @GetMapping("/stockMovements/{id}")
  public Object movement(@PathVariable String id) {
    return queries.movement(id);
  }

  @GetMapping("/stockAdjustments/{id}")
  public Object adjustment(@PathVariable String id) {
    return queries.adjustment(id);
  }
}
