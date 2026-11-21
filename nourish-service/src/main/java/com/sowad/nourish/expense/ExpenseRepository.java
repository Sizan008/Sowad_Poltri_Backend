package com.sowad.nourish.expense;

import com.sowad.nourish.common.masterdata.MasterDataException;
import com.sowad.nourish.report.ReportPeriod;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ExpenseRepository {
  private final JdbcTemplate jdbc;

  public ExpenseRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public JdbcTemplate jdbc() {
    return jdbc;
  }

  private static final String SELECT =
      """
SELECT id,title,category,method,amount,spent_at AS "spentAt",reference,note,revision,deleted,
created_at AS "createdAt",updated_at AS "updatedAt",created_by AS "createdBy",updated_by AS "updatedBy"
FROM nourish.expense
""";

  public Map<String, Object> get(String id, boolean lock, boolean includeDeleted) {
    var rows =
        jdbc.queryForList(
            SELECT
                + " WHERE id=?"
                + (includeDeleted ? "" : " AND NOT deleted")
                + (lock ? " FOR UPDATE" : ""),
            id);
    if (rows.isEmpty()) throw MasterDataException.missing("Expense");
    return rows.getFirst();
  }

  public List<Map<String, Object>> list(
      ReportPeriod period, String q, String category, String method, int limit, int offset) {
    String term = ReportPeriod.search(q);
    return jdbc.queryForList(
        SELECT
            + " WHERE NOT deleted AND (?::timestamptz IS NULL OR spent_at>=?) AND (?::timestamptz"
            + " IS NULL OR spent_at<?) AND (?::text IS NULL OR category=?) AND (?::text IS NULL OR"
            + " method=?) AND (?::text IS NULL OR title ILIKE ? ESCAPE '!' OR reference ILIKE ?"
            + " ESCAPE '!' OR note ILIKE ? ESCAPE '!') ORDER BY spent_at DESC,id DESC LIMIT ?"
            + " OFFSET ?",
        period.start(),
        period.start(),
        period.end(),
        period.end(),
        category,
        category,
        method,
        method,
        term,
        term,
        term,
        term,
        limit,
        offset);
  }

  public void history(String id, String action, String actor) {
    jdbc.update(
        "INSERT INTO nourish.expense_history(expense_id,revision,action,actor,snapshot) SELECT"
            + " id,revision,?,?,to_jsonb(e) FROM nourish.expense e WHERE id=?",
        action,
        actor,
        id);
  }

  public List<Map<String, Object>> history(String id) {
    get(id, false, true);
    return jdbc.queryForList(
        "SELECT revision,action,actor,occurred_at AS \"occurredAt\",snapshot::text AS snapshot FROM"
            + " nourish.expense_history WHERE expense_id=? ORDER BY revision",
        id);
  }
}
