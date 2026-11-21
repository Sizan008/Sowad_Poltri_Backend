package com.sowad.nourish.report;

import jakarta.validation.constraints.*;
import java.time.LocalDate;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
public class ReportController {
  private final ReportQueries queries;

  public ReportController(ReportQueries queries) {
    this.queries = queries;
  }

  @GetMapping("/reports/{type}")
  public Object report(
      @PathVariable @Pattern(regexp = "sales|purchases|stock|dues|expenses") String type,
      @RequestParam(required = false) LocalDate from,
      @RequestParam(required = false) LocalDate to,
      @RequestParam(required = false) String customerId,
      @RequestParam(required = false) String supplierId,
      @RequestParam(required = false) String productId,
      @RequestParam(required = false) String category,
      @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit,
      @RequestParam(defaultValue = "0") @Min(0) int offset) {
    com.sowad.nourish.inventory.StockNumbers.require(
        (customerId == null || type.equals("sales") || type.equals("dues"))
            && (supplierId == null || type.equals("purchases"))
            && (productId == null || type.equals("stock"))
            && (category == null || type.equals("stock") || type.equals("expenses")),
        "Filter is not supported for this report type.");
    return queries.report(
        type,
        ReportPeriod.of(from, to, !type.equals("stock") && !type.equals("dues")),
        type.equals("purchases") ? supplierId : type.equals("stock") ? productId : customerId,
        category,
        limit,
        offset);
  }
}
