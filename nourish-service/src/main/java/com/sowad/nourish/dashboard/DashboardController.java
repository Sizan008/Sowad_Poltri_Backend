package com.sowad.nourish.dashboard;

import com.sowad.nourish.report.ReportPeriod;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
public class DashboardController {
  private final DashboardQueries queries;

  public DashboardController(DashboardQueries queries) {
    this.queries = queries;
  }

  @GetMapping({"/dashboard", "/dashboard/summary"})
  public Object summary(@RequestParam(required = false) LocalDate date) {
    return queries.dashboard(date);
  }

  @GetMapping("/dashboard/chart")
  public Object chart(
      @RequestParam(required = false) LocalDate from,
      @RequestParam(required = false) LocalDate to) {
    if (from == null && to == null) {
      to = ReportPeriod.today();
      from = to.minusDays(6);
    }
    return queries.chart(ReportPeriod.of(from, to, false));
  }

  @GetMapping("/dashboard/recent-invoices")
  public Object recent(@RequestParam(defaultValue = "5") @Min(1) @Max(200) int limit) {
    return queries.recent(limit);
  }

  @GetMapping("/dashboard/reminders")
  public Object reminders(
      @RequestParam(defaultValue = "20") @Min(1) @Max(200) int limit,
      @RequestParam(defaultValue = "0") @Min(0) int offset) {
    return queries.reminders(limit, offset);
  }

  @GetMapping("/dashboard/low-stock")
  public Object low(
      @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit,
      @RequestParam(defaultValue = "0") @Min(0) int offset) {
    return queries.low(limit, offset);
  }
}
