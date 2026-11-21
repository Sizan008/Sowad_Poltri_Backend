package com.sowad.nourish.report;

import java.util.*;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ReportRepository {
  private final NamedParameterJdbcTemplate jdbc;

  public ReportRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Map<String, Object> run(
      String type, ReportPeriod period, String party, String category, int limit, int offset) {
    Map<String, Object> p = new HashMap<>();
    p.put("start", period.start());
    p.put("end", period.end());
    p.put("party", party);
    p.put("category", category);
    p.put("limit", limit);
    p.put("offset", offset);
    String base, order, totals;
    switch (type) {
      case "sales" -> {
        base =
            """
SELECT id,invoice_number AS "invoiceNumber",customer_snapshot->>'name_en' AS "customerName",
issued_at AS date,total_amount AS "invoiceAmount",paid_amount AS "paidAmount",due_amount AS "dueAmount"
FROM nourish.report_invoice_balance
WHERE issued_at>=:start AND issued_at<:end AND (:party::text IS NULL OR customer_id=:party)
""";
        order = "date DESC,id DESC";
        totals =
            "sum(\"invoiceAmount\") AS \"invoiceAmount\",sum(\"paidAmount\") AS"
                + " \"paidAmount\",sum(\"dueAmount\") AS \"dueAmount\"";
      }
      case "purchases" -> {
        base =
            """
SELECT id,purchase_number AS "purchaseNumber",supplier_name AS "supplierName",purchased_at AS date,total_amount AS "totalAmount"
FROM nourish.purchase WHERE purchased_at>=:start AND purchased_at<:end AND (:party::text IS NULL OR supplier_id=:party)
""";
        order = "date DESC,id DESC";
        totals = "sum(\"totalAmount\") AS \"totalAmount\"";
      }
      case "dues" -> {
        base =
            """
SELECT c.id,c.name_en AS "customerName",c.phone,count(*) AS "invoiceCount",
sum(i.total_amount) AS "invoiceAmount",sum(i.paid_amount) AS "paidAmount",sum(i.due_amount) AS "dueAmount",
min(i.due_date) FILTER(WHERE i.due_amount>0) AS "earliestDueDate"
FROM nourish.report_invoice_balance i JOIN nourish.customer c ON c.id=i.customer_id
WHERE (:start::timestamptz IS NULL OR i.issued_at>=:start) AND (:end::timestamptz IS NULL OR i.issued_at<:end)
AND (:party::text IS NULL OR c.id=:party)
GROUP BY c.id,c.name_en,c.phone HAVING sum(i.due_amount)>0
""";
        order = "\"dueAmount\" DESC,id";
        totals =
            "sum(\"invoiceCount\") AS \"invoiceCount\",sum(\"invoiceAmount\") AS"
                + " \"invoiceAmount\",sum(\"paidAmount\") AS \"paidAmount\",sum(\"dueAmount\") AS"
                + " \"dueAmount\"";
      }
      case "expenses" -> {
        base =
            """
SELECT id,title,category,method,amount,spent_at AS date,reference,note
FROM nourish.expense WHERE NOT deleted AND spent_at>=:start AND spent_at<:end AND (:category::text IS NULL OR category=:category)
""";
        order = "date DESC,id DESC";
        totals = "sum(amount) AS amount";
      }
      case "stock" -> {
        base =
            """
SELECT p.id,p.name AS "productName",p.sku,p.unit_code AS unit,p.category_id AS "categoryId",p.archived,
CASE WHEN :end::timestamptz IS NULL THEN coalesce(b.quantity,0) ELSE coalesce(m.closing,0) END AS quantity,
coalesce(m.opening,0) AS "openingQuantity",coalesce(m.change,0) AS "quantityChange",
coalesce(pr.cost_price,0) AS "costPrice",
round((CASE WHEN :end::timestamptz IS NULL THEN coalesce(b.quantity,0) ELSE coalesce(m.closing,0) END)*coalesce(pr.cost_price,0),2) AS "stockValue",
(CASE WHEN :end::timestamptz IS NULL THEN coalesce(b.quantity,0) ELSE coalesce(m.closing,0) END)<=p.low_stock_threshold AS "isLow"
FROM nourish.product p LEFT JOIN nourish.inventory_balance b ON b.product_id=p.id
LEFT JOIN nourish.product_price pr ON pr.product_id=p.id
LEFT JOIN (SELECT product_id,sum(quantity_change) FILTER(WHERE occurred_at<:start) AS opening,
sum(quantity_change) FILTER(WHERE occurred_at<:end) AS closing,
sum(quantity_change) FILTER(WHERE occurred_at>=:start AND occurred_at<:end) AS change
FROM nourish.stock_movement WHERE :end::timestamptz IS NOT NULL AND occurred_at<:end GROUP BY product_id) m ON m.product_id=p.id
WHERE (:category::text IS NULL OR p.category_id=:category) AND (:party::text IS NULL OR p.id=:party)
AND (:end::timestamptz IS NOT NULL OR NOT p.archived)
""";
        order = "\"productName\",id";
        totals = "sum(\"stockValue\") AS \"stockValue\"";
      }
      default ->
          throw new com.sowad.nourish.common.masterdata.MasterDataException(
              400, "Unknown report type.");
    }
    var summary =
        jdbc.queryForMap("SELECT count(*) AS count," + totals + " FROM (" + base + ") report", p);
    summary.replaceAll((k, v) -> v == null ? java.math.BigDecimal.ZERO : v);
    var result = period.metadata();
    result.put("type", type);
    result.put("total", summary.get("count"));
    result.put("summary", summary);
    result.put(
        "items",
        jdbc.queryForList(base + " ORDER BY " + order + " LIMIT :limit OFFSET :offset", p));
    result.put("limit", limit);
    result.put("offset", offset);
    if (type.equals("sales") || type.equals("dues"))
      result.put("paymentBasis", "current collections against selected invoices");
    if (type.equals("stock")) {
      result.put(
          "quantityBasis",
          period.from() == null
              ? "current balance"
              : "movement business dates through inclusive to date");
      result.put("valuationBasis", "latest purchase unit cost; not historical/FIFO valuation");
      result.put(
          "quantitiesByUnit",
          jdbc.queryForList(
              "SELECT unit,sum(quantity) AS quantity FROM ("
                  + base
                  + ") report GROUP BY unit ORDER BY unit",
              p));
    }
    if (type.equals("expenses"))
      result.put(
          "byCategory",
          jdbc.queryForList(
              "SELECT category,sum(amount) AS amount FROM ("
                  + base
                  + ") report GROUP BY category ORDER BY category",
              p));
    return result;
  }
}
