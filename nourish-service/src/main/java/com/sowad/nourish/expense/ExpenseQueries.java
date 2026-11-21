package com.sowad.nourish.expense;

import com.sowad.nourish.invoice.SalesSupport;
import com.sowad.nourish.report.ReportPeriod;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
@PreAuthorize("hasAnyRole('admin','operator','viewer')")
public class ExpenseQueries {
  private final ExpenseRepository repository;
  private final SalesSupport json;

  public ExpenseQueries(ExpenseRepository repository, SalesSupport json) {
    this.repository = repository;
    this.json = json;
  }

  public Object list(
      ReportPeriod period, String q, String category, String method, int limit, int offset) {
    return repository.list(period, q, category, method, limit, offset);
  }

  public Object get(String id) {
    return repository.get(id, false, false);
  }

  @PreAuthorize("hasRole('admin')")
  public Object history(String id) {
    var rows = repository.history(id);
    for (var r : rows) r.put("snapshot", json.decode((String) r.get("snapshot")));
    return rows;
  }
}
