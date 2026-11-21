package com.sowad.nourish.dashboard;

import com.sowad.nourish.report.ReportPeriod;
import java.time.LocalDate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
@PreAuthorize("hasAnyRole('admin','operator','viewer')")
public class DashboardQueries {
  private final DashboardRepository repository;

  public DashboardQueries(DashboardRepository repository) {
    this.repository = repository;
  }

  public Object dashboard(LocalDate date) {
    date = date == null ? ReportPeriod.today() : date;
    var range = ReportPeriod.of(date.minusDays(6), date, false);
    var result = repository.summary(date);
    result.put("chartPoints", repository.chart(range));
    result.put("recentInvoices", repository.recent(5));
    result.put("reminders", repository.reminders(3, 0));
    result.put("stockAlerts", repository.low(200, 0));
    return result;
  }

  public Object chart(ReportPeriod range) {
    com.sowad.nourish.inventory.StockNumbers.require(
        java.time.temporal.ChronoUnit.DAYS.between(range.from(), range.to()) <= 365,
        "Chart range is limited to 366 days.");
    return repository.chart(range);
  }

  public Object recent(int limit) {
    return repository.recent(limit);
  }

  public Object reminders(int limit, int offset) {
    return repository.reminders(limit, offset);
  }

  public Object low(int limit, int offset) {
    return repository.low(limit, offset);
  }
}
