package com.sowad.nourish.activitylog.repository;

import com.sowad.nourish.activitylog.query.ActivityLog;
import java.util.*;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcActivityLogQueryRepository {
  private final NamedParameterJdbcTemplate jdbc;

  public JdbcActivityLogQueryRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<ActivityLog> retrieveLogs(int limit, int offset) {
    return retrieveLogs(
        new com.sowad.nourish.activitylog.query.ActivityLogFilter(
            null, null, null, null, null, null, null, null, null),
        limit,
        offset);
  }

  public List<ActivityLog> retrieveLogs(
      com.sowad.nourish.activitylog.query.ActivityLogFilter f, int limit, int offset) {
    var range = com.sowad.nourish.report.ReportPeriod.of(f.from(), f.to(), false);
    Map<String, Object> p = new HashMap<>();
    p.put("limit", limit);
    p.put("offset", offset);
    StringBuilder sql = new StringBuilder("SELECT * FROM nourish.activity_log WHERE true");
    if (range.from() != null) {
      sql.append(" AND occurred_at>=:start AND occurred_at<:end");
      p.put("start", range.start());
      p.put("end", range.end());
    }
    String[] keys = {"actor_id", "role", "action", "entity", "entity_id"};
    String[] values = {f.actorId(), f.role(), f.action(), f.entity(), f.entityId()};
    for (int i = 0; i < keys.length; i++)
      if (values[i] != null) {
        sql.append(" AND ").append(keys[i]).append("=:").append(keys[i]);
        p.put(keys[i], values[i]);
      }
    String term = com.sowad.nourish.report.ReportPeriod.search(f.q());
    if (term != null) {
      sql.append(
          " AND (actor_name ILIKE :q ESCAPE '!' OR details ILIKE :q ESCAPE '!' OR entity_id ILIKE"
              + " :q ESCAPE '!')");
      p.put("q", term);
    }
    if (f.text() != null && !f.text().isBlank()) {
      sql.append(
          " AND to_tsvector('simple',coalesce(actor_name,'')||' '||coalesce(details,'')||'"
              + " '||coalesce(entity,'')||' '||coalesce(action,'')||' '||coalesce(entity_id,'')) @@"
              + " plainto_tsquery('simple',:text)");
      p.put("text", f.text());
    }
    sql.append(" ORDER BY occurred_at DESC,id DESC LIMIT :limit OFFSET :offset");
    return jdbc.query(
        sql.toString(),
        p,
        (rs, n) ->
            new ActivityLog(
                rs.getString("id"),
                rs.getString("actor_id"),
                rs.getString("actor_name"),
                rs.getString("role"),
                rs.getString("action"),
                rs.getString("entity"),
                rs.getString("entity_id"),
                rs.getString("details"),
                rs.getTimestamp("occurred_at").toInstant()));
  }
}
