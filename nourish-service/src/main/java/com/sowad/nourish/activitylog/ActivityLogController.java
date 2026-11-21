package com.sowad.nourish.activitylog;

import com.sowad.nourish.activitylog.query.ActivityLog;
import jakarta.validation.constraints.*;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
public class ActivityLogController {
  private final ActivityLogQueryService queries;

  public ActivityLogController(ActivityLogQueryService queries) {
    this.queries = queries;
  }

  @GetMapping("/activityLogs")
  public List<ActivityLog> retrieveLogs(
      @RequestParam(required = false) @Size(max = 120) String q,
      @RequestParam(required = false) @Size(max = 120) String text,
      @RequestParam(required = false) @Size(max = 64) String actorId,
      @RequestParam(required = false) @Pattern(regexp = "admin|operator|viewer|anonymous")
          String role,
      @RequestParam(required = false) @Size(max = 40) String action,
      @RequestParam(required = false) @Size(max = 40) String entity,
      @RequestParam(required = false) @Size(max = 64) String entityId,
      @RequestParam(required = false) java.time.LocalDate from,
      @RequestParam(required = false) java.time.LocalDate to,
      @RequestParam(defaultValue = "100") @Min(1) @Max(200) int limit,
      @RequestParam(defaultValue = "0") @Min(0) int offset) {
    return queries.retrieveLogs(
        new com.sowad.nourish.activitylog.query.ActivityLogFilter(
            q, text, actorId, role, action, entity, entityId, from, to),
        limit,
        offset);
  }
}
