package com.sowad.nourish.report;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
@PreAuthorize("hasAnyRole('admin','operator','viewer')")
public class ReportQueries {
  private final ReportRepository repository;

  public ReportQueries(ReportRepository repository) {
    this.repository = repository;
  }

  public Object report(
      String type, ReportPeriod period, String party, String category, int limit, int offset) {
    return repository.run(type, period, party, category, limit, offset);
  }
}
