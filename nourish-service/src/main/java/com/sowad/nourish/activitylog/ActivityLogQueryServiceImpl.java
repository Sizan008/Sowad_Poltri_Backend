package com.sowad.nourish.activitylog;

import com.sowad.nourish.activitylog.query.ActivityLog;
import com.sowad.nourish.activitylog.repository.JdbcActivityLogQueryRepository;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ActivityLogQueryServiceImpl implements ActivityLogQueryService {
  private final JdbcActivityLogQueryRepository repository;

  public ActivityLogQueryServiceImpl(JdbcActivityLogQueryRepository repository) {
    this.repository = repository;
  }

  @Override
  @Transactional(readOnly = true)
  @PreAuthorize("hasRole('admin')")
  public List<ActivityLog> retrieveLogs(int limit, int offset) {
    return repository.retrieveLogs(limit, offset);
  }

  @Transactional(readOnly = true)
  @PreAuthorize("hasRole('admin')")
  public List<ActivityLog> retrieveLogs(
      com.sowad.nourish.activitylog.query.ActivityLogFilter filter, int limit, int offset) {
    return repository.retrieveLogs(filter, limit, offset);
  }
}
