package com.sowad.nourish.payment;

import com.sowad.nourish.common.crypto.AccountProtection;

import static com.sowad.nourish.inventory.StockNumbers.*;

import com.sowad.nourish.common.masterdata.MasterDataException;
import com.sowad.nourish.invoice.SalesSupport;
import com.sowad.nourish.payment.command.CollectPayment;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class PaymentPosting {
  private final JdbcTemplate jdbc;
  private final AccountProtection protection;
  private final PaymentAccountCapture accounts;
  private final SalesSupport json;

  public PaymentPosting(JdbcTemplate jdbc, PaymentAccountCapture accounts, SalesSupport json, AccountProtection protection) {
    this.jdbc = jdbc;
    this.protection = protection;
    this.accounts = accounts;
    this.json = json;
  }

  public String post(CollectPayment c, String actor) {
    SalesSupport.moneyInput(c.amount(), "Payment amount", false);
    SalesSupport.date(c.paidAt(), "Payment date");
    var rows =
        jdbc.queryForList("SELECT * FROM nourish.invoice WHERE id=? FOR UPDATE", c.invoiceId());
    if (rows.isEmpty()) throw MasterDataException.missing("Invoice");
    var invoice = rows.getFirst();
    require(
        !c.paidAt().isBefore(((Timestamp) invoice.get("issued_at")).toInstant()),
        "Payment date cannot precede invoice issue time.");
    BigDecimal paid =
        jdbc.queryForObject(
            "SELECT coalesce(sum(amount),0) FROM nourish.payment WHERE invoice_id=?",
            BigDecimal.class,
            c.invoiceId());
    if (c.amount().compareTo(((BigDecimal) invoice.get("total_amount")).subtract(paid)) > 0)
      throw MasterDataException.conflict("Payment exceeds remaining invoice due.");
    var account =
        accounts.capture(
            c.method(),
            c.receivingAccountId(),
            c.paymentProvider(),
            c.senderNumber(),
            c.transactionRef());
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO"
            + " nourish.payment(id,invoice_id,customer_id,amount,paid_at,method,receiving_account_id,payment_provider,sender_number,transaction_ref,account_snapshot,note,actor)"
            + " VALUES (?,?,?,?,?,?,?,?,?,?,?::jsonb,?,?)",
        id,
        c.invoiceId(),
        invoice.get("customer_id"),
        c.amount(),
        Timestamp.from(c.paidAt()),
        account.method(),
        account.accountId(),
        account.provider(),
        account.sender(),
        account.reference(),
        account.snapshot() == null ? null : json.encode(protection.transform(new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(account.snapshot()), "payment/" + id, true)),
        text(c.note(), 500, "Payment note"),
        actor);
    return id;
  }
}
