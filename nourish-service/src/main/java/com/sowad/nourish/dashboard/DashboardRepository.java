package com.sowad.nourish.dashboard;

import com.sowad.nourish.report.ReportPeriod;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DashboardRepository {
  private final JdbcTemplate jdbc;

  public DashboardRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Map<String, Object> summary(LocalDate day) {
    var range = ReportPeriod.of(day, day, false);
    var yesterday = Timestamp.from(day.minusDays(1).atStartOfDay(ReportPeriod.ZONE).toInstant());
    var month = Timestamp.from(day.withDayOfMonth(1).atStartOfDay(ReportPeriod.ZONE).toInstant());
    var nextMonth =
        Timestamp.from(
            day.withDayOfMonth(1).plusMonths(1).atStartOfDay(ReportPeriod.ZONE).toInstant());
    var m =
        jdbc.queryForMap(
            """
SELECT
(SELECT coalesce(sum(total_amount),0) FROM nourish.invoice WHERE issued_at>=? AND issued_at<?) AS "todaySales",
(SELECT coalesce(sum(total_amount),0) FROM nourish.invoice WHERE issued_at>=? AND issued_at<?) AS "yesterdaySales",
(SELECT coalesce(sum(due_amount),0) FROM nourish.report_invoice_balance) AS "totalDue",
(SELECT count(DISTINCT customer_id) FROM nourish.report_invoice_balance WHERE due_amount>0) AS "dueCustomerCount",
(SELECT coalesce(round(sum(coalesce(b.quantity,0)*coalesce(pr.cost_price,0)),2),0) FROM nourish.product p LEFT JOIN nourish.inventory_balance b ON b.product_id=p.id LEFT JOIN nourish.product_price pr ON pr.product_id=p.id WHERE NOT p.archived) AS "stockValue",
(SELECT coalesce(sum(b.quantity),0) FROM nourish.product p JOIN nourish.inventory_balance b ON b.product_id=p.id WHERE NOT p.archived AND p.unit_code='kg') AS "totalStockWeightKg",
(SELECT count(*) FROM nourish.customer WHERE NOT archived AND status='active') AS "activeCustomers",
(SELECT count(*) FROM nourish.customer WHERE created_at>=? AND created_at<?) AS "newCustomersThisMonth",
(SELECT count(*) FROM nourish.product p LEFT JOIN nourish.inventory_balance b ON b.product_id=p.id WHERE NOT p.archived AND coalesce(b.quantity,0)<=p.low_stock_threshold) AS "stockAlertCount"
""",
            range.start(),
            range.end(),
            yesterday,
            range.start(),
            month,
            nextMonth);
    var today = (java.math.BigDecimal) m.get("todaySales");
    var prior = (java.math.BigDecimal) m.get("yesterdaySales");
    m.put(
        "salesGrowthPercent",
        prior.signum() == 0
            ? java.math.BigDecimal.ZERO
            : today
                .subtract(prior)
                .multiply(new java.math.BigDecimal("100"))
                .divide(prior, 2, java.math.RoundingMode.HALF_UP));
    m.put("date", day);
    m.put("timeZone", ReportPeriod.ZONE.toString());
    m.put("balanceBasis", "current due and stock; date anchors sales/chart only");
    return m;
  }

  public List<Map<String, Object>> chart(ReportPeriod range) {
    return jdbc.queryForList(
        """
WITH days AS (SELECT generate_series(?::date,?::date,interval '1 day')::date AS day),
s AS (SELECT (issued_at AT TIME ZONE 'Asia/Dhaka')::date AS day,sum(total_amount) AS amount FROM nourish.invoice WHERE issued_at>=? AND issued_at<? GROUP BY 1),
p AS (SELECT (purchased_at AT TIME ZONE 'Asia/Dhaka')::date AS day,sum(total_amount) AS amount FROM nourish.purchase WHERE purchased_at>=? AND purchased_at<? GROUP BY 1)
SELECT to_char(days.day,'YYYY-MM-DD') AS date,to_char(days.day,'DD Mon') AS label,coalesce(s.amount,0) AS sales,coalesce(p.amount,0) AS purchases
FROM days LEFT JOIN s USING(day) LEFT JOIN p USING(day) ORDER BY days.day
""",
        range.from(),
        range.to(),
        range.start(),
        range.end(),
        range.start(),
        range.end());
  }

  public List<Map<String, Object>> recent(int limit) {
    return jdbc.queryForList(
        """
SELECT id,invoice_number AS "invoiceNumber",customer_snapshot->>'name_en' AS "customerName",issued_at AS "issuedAt",total_amount AS "totalAmount",due_amount AS "dueAmount",
CASE WHEN due_amount=0 THEN 'paid' WHEN paid_amount>0 THEN 'partial' ELSE 'due' END AS status
FROM nourish.report_invoice_balance ORDER BY issued_at DESC,id DESC LIMIT ?
""",
        limit);
  }

  public List<Map<String, Object>> reminders(int limit, int offset) {
    return jdbc.queryForList(
        """
SELECT c.id AS "customerId",c.name_en AS "customerName",c.phone,min(i.due_date) AS "dueDate",sum(i.due_amount) AS amount
FROM nourish.report_invoice_balance i JOIN nourish.customer c ON c.id=i.customer_id
WHERE i.due_amount>0 GROUP BY c.id,c.name_en,c.phone ORDER BY min(i.due_date),c.id LIMIT ? OFFSET ?
""",
        limit,
        offset);
  }

  public List<Map<String, Object>> low(int limit, int offset) {
    return jdbc.queryForList(
        """
SELECT p.id,p.name,coalesce(b.quantity,0) AS quantity,p.unit_code AS unit,p.low_stock_threshold AS "lowStockThreshold"
FROM nourish.product p LEFT JOIN nourish.inventory_balance b ON b.product_id=p.id
WHERE NOT p.archived AND coalesce(b.quantity,0)<=p.low_stock_threshold ORDER BY p.name,p.id LIMIT ? OFFSET ?
""",
        limit,
        offset);
  }
}
