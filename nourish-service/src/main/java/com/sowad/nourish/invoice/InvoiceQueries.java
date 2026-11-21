package com.sowad.nourish.invoice;

import com.sowad.nourish.common.crypto.AccountProtection;

import com.sowad.nourish.common.masterdata.MasterDataException;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
@PreAuthorize("hasAnyRole('admin','operator','viewer')")
public class InvoiceQueries {
  private final JdbcTemplate jdbc;
  private final AccountProtection protection;
  private final SalesSupport json;

  public InvoiceQueries(JdbcTemplate jdbc, SalesSupport json, AccountProtection protection) {
    this.jdbc = jdbc;
    this.protection = protection;
    this.json = json;
  }

  private static final String HEAD =
      """
SELECT i.id,i.invoice_number AS "invoiceNumber",i.customer_id AS "customerId",
i.issued_at AS "issuedAt",i.due_date AS "dueDate",i.subtotal,i.discount_amount AS "discountAmount",
i.total_amount AS "totalAmount",coalesce(p.paid,0) AS "paidAmount",i.total_amount-coalesce(p.paid,0) AS "dueAmount",
CASE WHEN coalesce(p.paid,0)=i.total_amount THEN 'paid' WHEN coalesce(p.paid,0)>0 THEN 'partial' ELSE 'unpaid' END AS status,
i.notes,i.last_note AS "lastNote",i.created_at AS "createdAt"
FROM nourish.invoice i LEFT JOIN (SELECT invoice_id,sum(amount) AS paid FROM nourish.payment GROUP BY invoice_id) p ON p.invoice_id=i.id
""";

  public List<Map<String, Object>> list(String customer, String status, int limit, int offset) {
    return jdbc.queryForList(
        "SELECT * FROM ("
            + HEAD
            + ") v WHERE (?::text IS NULL OR v.\"customerId\"=?) AND (?::text IS NULL OR status=?)"
            + " ORDER BY \"issuedAt\" DESC,id DESC LIMIT ? OFFSET ?",
        customer,
        customer,
        status,
        status,
        limit,
        offset);
  }

  public Map<String, Object> retrieve(String id) {
    var rows = jdbc.queryForList(HEAD + " WHERE i.id=?", id);
    if (rows.isEmpty()) throw MasterDataException.missing("Invoice");
    var row = rows.getFirst();
    var snap =
        jdbc.queryForMap(
            "SELECT customer_snapshot::text AS customer,business_snapshot::text AS business FROM"
                + " nourish.invoice WHERE id=?",
            id);
    row.put("customerSnapshot", json.decode((String) snap.get("customer")));
    row.put("businessSnapshot", json.decode((String) snap.get("business")));
    row.put("items", items(id));
    row.put("payments", payments(id, null, 10000, 0));
    return row;
  }

  public List<Map<String, Object>> items(String id) {
    var rows =
        jdbc.queryForList(
            """
SELECT id,invoice_id AS "invoiceId",product_id AS "productId",product_name AS "productName",sku,
category_id AS "categoryId",category_name AS "categoryName",unit_code AS unit,quantity,unit_price AS "unitPrice",
line_total AS "lineTotal",rate_snapshot::text AS snapshot FROM nourish.invoice_item WHERE invoice_id=? ORDER BY line_number
""",
            id);
    for (var row : rows) row.put("rateSnapshot", json.decode((String) row.remove("snapshot")));
    for (var row : rows) {
      var details =
          jdbc.queryForList(
              "SELECT d.size_base AS \"sizeBase\",d.package_count AS count,p.amount,p.unit_code AS"
                  + " \"unitCode\" FROM nourish.invoice_package_detail d LEFT JOIN"
                  + " nourish.product_packaging p ON p.id=d.packaging_id WHERE d.invoice_item_id=?"
                  + " ORDER BY d.size_base",
              row.get("id"));
      if ("kg".equals(row.get("unit")))
        row.put(
            "packages",
            details.stream()
                .map(d -> Map.of("bagSizeKg", d.get("sizeBase"), "bagCount", d.get("count")))
                .toList());
      if (Set.of("g", "ml").contains(row.get("unit")))
        row.put(
            "medicinePacks",
            details.stream()
                .map(
                    d ->
                        Map.of(
                            "amount",
                            d.get("amount"),
                            "unitCode",
                            d.get("unitCode"),
                            "packCount",
                            d.get("count")))
                .toList());
    }
    return rows;
  }

  private static final String PAY =
      """
SELECT id,invoice_id AS "invoiceId",customer_id AS "customerId",amount,paid_at AS "paidAt",method,
receiving_account_id AS "receivingAccountId",payment_provider AS "paymentProvider",sender_number AS "senderNumber",
transaction_ref AS "transactionRef",account_snapshot::text AS snapshot,note,created_at AS "createdAt"
FROM nourish.payment
""";

  private List<Map<String, Object>> parsePayments(List<Map<String, Object>> rows) {
    for (var row : rows) {
      String value = (String) row.remove("snapshot");
      row.put("receivingAccountSnapshot", value == null ? null : json.decode(json.encode(protection.transform(new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(json.decode(value)), "payment/" + row.get("id"), false))));
    }
    return rows;
  }

  public List<Map<String, Object>> payments(
      String invoice, String customer, int limit, int offset) {
    return parsePayments(
        jdbc.queryForList(
            PAY
                + " WHERE (?::text IS NULL OR invoice_id=?) AND (?::text IS NULL OR customer_id=?)"
                + " ORDER BY paid_at,id LIMIT ? OFFSET ?",
            invoice,
            invoice,
            customer,
            customer,
            limit,
            offset));
  }

  public Map<String, Object> payment(String id) {
    var rows = parsePayments(jdbc.queryForList(PAY + " WHERE id=?", id));
    if (rows.isEmpty()) throw MasterDataException.missing("Payment");
    return rows.getFirst();
  }
}
