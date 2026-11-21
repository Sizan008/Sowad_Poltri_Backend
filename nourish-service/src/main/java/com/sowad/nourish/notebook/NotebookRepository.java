package com.sowad.nourish.notebook;

import com.sowad.nourish.common.masterdata.MasterDataException;
import com.sowad.nourish.report.ReportPeriod;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class NotebookRepository {
  private final JdbcTemplate jdbc;

  public NotebookRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public JdbcTemplate jdbc() {
    return jdbc;
  }

  private static final String SELECT =
      """
SELECT id,serial,title,content,author_id AS "authorId",author_name AS "authorName",
created_at AS "createdAt",updated_at AS "updatedAt",revision,deleted FROM nourish.notebook
""";

  public Map<String, Object> get(String id, boolean lock, boolean includeDeleted) {
    var rows =
        jdbc.queryForList(
            SELECT
                + " WHERE id=?"
                + (includeDeleted ? "" : " AND NOT deleted")
                + (lock ? " FOR UPDATE" : ""),
            id);
    if (rows.isEmpty()) throw MasterDataException.missing("Note");
    return rows.getFirst();
  }

  public Map<String, Object> page(int page, int size, String q, String order) {
    String term = ReportPeriod.search(q);
    String filter =
        " WHERE NOT deleted AND (?::text IS NULL OR title ILIKE ? ESCAPE '!' OR content ILIKE ?"
            + " ESCAPE '!')";
    long count =
        jdbc.queryForObject(
            "SELECT count(*) FROM nourish.notebook" + filter, Long.class, term, term, term);
    var items =
        jdbc.queryForList(
            SELECT
                + filter
                + " ORDER BY serial "
                + (order.equals("asc") ? "ASC" : "DESC")
                + " LIMIT ? OFFSET ?",
            term,
            term,
            term,
            size,
            ((long) page - 1) * size);
    return Map.of("items", items, "total", count, "page", page, "size", size);
  }
}
