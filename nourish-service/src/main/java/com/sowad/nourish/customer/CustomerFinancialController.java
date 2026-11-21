package com.sowad.nourish.customer;

import jakarta.validation.constraints.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
public class CustomerFinancialController {
  private final CustomerFinancialQueries queries;

  public CustomerFinancialController(CustomerFinancialQueries queries) {
    this.queries = queries;
  }

  @GetMapping("/customerInsights")
  public Object list(
      @RequestParam(required = false) @Pattern(regexp = "due|paid|no-invoices") String billCategory,
      @RequestParam(required = false) String buyingCategoryId,
      @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit,
      @RequestParam(defaultValue = "0") @Min(0) int offset) {
    return queries.list(billCategory, buyingCategoryId, limit, offset);
  }

  @GetMapping("/customers/{id}/due")
  public Object due(@PathVariable String id) {
    return queries.summary(id);
  }

  @GetMapping("/customers/{id}/ledger")
  public Object ledger(
      @PathVariable String id,
      @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit,
      @RequestParam(defaultValue = "0") @Min(0) int offset) {
    return queries.ledger(id, limit, offset);
  }
}
