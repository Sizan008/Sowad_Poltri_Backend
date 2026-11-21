package com.sowad.nourish.expense;

import com.sowad.nourish.common.masterdata.StrictCommandDecoder;
import com.sowad.nourish.expense.command.SaveExpense;
import com.sowad.nourish.report.ReportPeriod;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
public class ExpenseController {
  private final ExpenseService service;
  private final ExpenseQueries queries;
  private final StrictCommandDecoder decoder;

  public ExpenseController(
      ExpenseService service, ExpenseQueries queries, StrictCommandDecoder decoder) {
    this.service = service;
    this.queries = queries;
    this.decoder = decoder;
  }

  @GetMapping("/expenses")
  public Object list(
      @RequestParam(required = false) LocalDate from,
      @RequestParam(required = false) LocalDate to,
      @RequestParam(required = false) @Size(max = 120) String q,
      @RequestParam(required = false)
          @Pattern(regexp = "rent|utilities|salary|transport|maintenance|supplies|other")
          String category,
      @RequestParam(required = false) @Pattern(regexp = "cash|bank|mobile-banking") String method,
      @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit,
      @RequestParam(defaultValue = "0") @Min(0) int offset) {
    return queries.list(ReportPeriod.of(from, to, false), q, category, method, limit, offset);
  }

  @GetMapping("/expenses/{id}")
  public Object get(@PathVariable String id) {
    return queries.get(id);
  }

  @GetMapping("/expenses/{id}/history")
  public Object history(@PathVariable String id) {
    return queries.history(id);
  }

  @PostMapping("/transactions/expenses")
  @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
  public Object create(@RequestHeader("Idempotency-Key") String key, @RequestBody String body) {
    return service.create(key, decoder.decode(body, SaveExpense.class));
  }

  @PutMapping("/transactions/expenses/{id}")
  public Object update(@PathVariable String id, @RequestBody String body) {
    return service.update(id, decoder.decode(body, SaveExpense.class));
  }

  @DeleteMapping("/transactions/expenses/{id}")
  public Object delete(
      @PathVariable String id, @RequestParam(required = false) @Min(0) Long expectedRevision) {
    return service.delete(id, expectedRevision);
  }
}
