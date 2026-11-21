package com.sowad.nourish.report;

import com.sowad.nourish.contact.PartyDetailsQuery;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
@PreAuthorize("hasAnyRole('admin','operator','viewer')")
public class PartyDetailsQueries implements PartyDetailsQuery {
  private final JdbcTemplate jdbc;

  public PartyDetailsQueries(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Map<String, Object> supplier(String id) {
    var summary =
        jdbc.queryForMap(
            "SELECT count(*) AS \"purchaseCount\",coalesce(sum(total_amount),0) AS"
                + " \"purchaseAmount\",max(purchased_at) AS \"lastPurchaseAt\" FROM"
                + " nourish.purchase WHERE supplier_id=?",
            id);
    summary.put("paymentSummaryAvailable", false);
    summary.put(
        "recentPurchases",
        jdbc.queryForList(
            "SELECT id,purchase_number AS \"purchaseNumber\",purchased_at AS"
                + " \"purchasedAt\",total_amount AS \"totalAmount\" FROM nourish.purchase WHERE"
                + " supplier_id=? ORDER BY purchased_at DESC,id DESC LIMIT 5",
            id));
    return summary;
  }

  public Map<String, Object> customer(String id) {
    var result = new LinkedHashMap<String, Object>();
    result.put(
        "recentInvoices",
        jdbc.queryForList(
            "SELECT id,invoice_number AS \"invoiceNumber\",issued_at AS \"issuedAt\",total_amount"
                + " AS \"totalAmount\",paid_amount AS \"paidAmount\",due_amount AS \"dueAmount\""
                + " FROM nourish.report_invoice_balance WHERE customer_id=? ORDER BY issued_at"
                + " DESC,id DESC LIMIT 5",
            id));
    result.put(
        "recentPayments",
        jdbc.queryForList(
            "SELECT id,invoice_id AS \"invoiceId\",amount,paid_at AS \"paidAt\",method FROM"
                + " nourish.payment WHERE customer_id=? ORDER BY paid_at DESC,id DESC LIMIT 5",
            id));
    return result;
  }
}
