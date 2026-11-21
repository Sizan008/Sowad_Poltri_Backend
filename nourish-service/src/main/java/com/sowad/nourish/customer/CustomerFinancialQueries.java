package com.sowad.nourish.customer;

import com.sowad.nourish.common.masterdata.MasterDataException;
import com.sowad.nourish.invoice.SalesSupport;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
@PreAuthorize("hasAnyRole('admin','operator','viewer')")
public class CustomerFinancialQueries implements CustomerFinancialQuery {
  private final JdbcTemplate jdbc;
  private final SalesSupport json;

  public CustomerFinancialQueries(JdbcTemplate jdbc, SalesSupport json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  private static final String SELECT =
      """
SELECT customer_id AS "customerId",invoice_count AS "invoiceCount",billed_amount AS "billedAmount",
paid_amount AS "paidAmount",due_amount AS "dueAmount",bill_category AS "billCategory",
buying_categories::text AS categories FROM nourish.customer_financial_summary
""";

  private List<Map<String, Object>> parse(List<Map<String, Object>> rows) {
    for (var row : rows) {
      var categories = (List<Map<String, Object>>) json.decode((String) row.remove("categories"));
      row.put("buyingCategories", categories);
      row.put("buyingCategoryIds", categories.stream().map(v -> v.get("id")).toList());
      row.put("buyingCategoryNames", categories.stream().map(v -> v.get("name")).toList());
    }
    return rows;
  }

  public Map<String, Object> summary(String id) {
    var rows = parse(jdbc.queryForList(SELECT + " WHERE customer_id=?", id));
    if (rows.isEmpty()) throw MasterDataException.missing("Customer");
    return rows.getFirst();
  }

  public List<Map<String, Object>> list(String bill, String category, int limit, int offset) {
    return parse(
        jdbc.queryForList(
            SELECT
                + " WHERE (?::text IS NULL OR bill_category=?) AND (?::text IS NULL OR"
                + " buying_categories @> jsonb_build_array(jsonb_build_object('id',?::text))) ORDER"
                + " BY due_amount DESC,customer_id LIMIT ? OFFSET ?",
            bill,
            bill,
            category,
            category,
            limit,
            offset));
  }

  public Map<String, Object> ledger(String id, int limit, int offset) {
    var summary = summary(id);
    var rows =
        jdbc.queryForList(
            """
SELECT entry_id AS id,entry_type AS type,invoice_id AS "invoiceId",invoice_number AS "invoiceNumber",
occurred_at AS "occurredAt",debit,credit,
sum(debit-credit) OVER(ORDER BY occurred_at,entry_order,entry_id ROWS UNBOUNDED PRECEDING) AS "runningDue"
FROM nourish.customer_ledger WHERE customer_id=?
ORDER BY occurred_at,entry_order,entry_id LIMIT ? OFFSET ?
""",
            id,
            limit,
            offset);
    return Map.of("summary", summary, "entries", rows, "limit", limit, "offset", offset);
  }
}
