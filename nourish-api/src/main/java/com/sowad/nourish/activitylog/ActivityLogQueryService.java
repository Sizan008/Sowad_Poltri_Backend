package com.sowad.nourish.activitylog;

import com.sowad.nourish.activitylog.query.ActivityLog;
import java.util.List;

public interface ActivityLogQueryService {
  List<ActivityLog> retrieveLogs(int limit, int offset);

  List<ActivityLog> retrieveLogs(
      com.sowad.nourish.activitylog.query.ActivityLogFilter filter, int limit, int offset);
}
