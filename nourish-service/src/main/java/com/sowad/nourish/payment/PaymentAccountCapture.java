package com.sowad.nourish.payment;

import com.sowad.nourish.common.crypto.AccountProtection;

import static com.sowad.nourish.inventory.StockNumbers.*;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class PaymentAccountCapture {
  public record Captured(
      String method,
      String accountId,
      String provider,
      String sender,
      String reference,
      Map<String, Object> snapshot) {}

  private final JdbcTemplate jdbc;
  private final AccountProtection protection;

  public PaymentAccountCapture(JdbcTemplate jdbc, AccountProtection protection) {
    this.jdbc = jdbc;
    this.protection = protection;
  }

  public Captured capture(
      String method, String accountId, String provider, String sender, String reference) {
    require(
        method != null && Set.of("cash", "bank", "mobile-banking").contains(method),
        "Choose cash, bank or mobile-banking.");
    accountId = text(accountId, 64, "Account");
    provider = text(provider, 10, "Provider");
    sender = text(sender, 20, "Sender");
    reference = text(reference, 80, "Transaction reference");
    if (method.equals("cash")) {
      require(
          accountId.isEmpty() && provider.isEmpty() && sender.isEmpty() && reference.isEmpty(),
          "Cash must not contain bank/MFS metadata.");
      return new Captured(method, null, null, "", "", null);
    }
    var rows =
        jdbc.queryForList(
            "SELECT * FROM nourish.receiving_account WHERE id=? FOR UPDATE", accountId);
    require(
        !rows.isEmpty() && Boolean.TRUE.equals(rows.getFirst().get("active")),
        "Choose an active receiving account.");
    var a = rows.getFirst();
    Map<String, Object> snap = new LinkedHashMap<>();
    snap.put("kind", a.get("kind"));
    snap.put("label", a.get("label"));
    if (method.equals("bank")) {
      require(
          "bank".equals(a.get("kind")) && (provider.isEmpty() || provider.equals("bank")),
          "Choose matching bank account.");
      snap.put("bankName", a.get("bank_name"));
      snap.put("accountName", a.get("account_name"));
      snap.put("accountNumber", protection.decrypt((String) a.get("account_number"), "receiving/" + accountId + "/bank"));
      provider = "bank";
    } else {
      require(
          "mfs".equals(a.get("kind")) && provider.equals(a.get("provider")),
          "Choose matching bKash/Nagad provider and wallet.");
      snap.put("provider", provider);
      snap.put("walletNumber", protection.decrypt((String) a.get("wallet_number"), "receiving/" + accountId + "/wallet"));
    }
    if (!sender.isEmpty()) {
      sender = sender.replaceAll("[\\s()\\-]", "");
      if (sender.startsWith("+88")) sender = sender.substring(3);
      else if (sender.startsWith("88")) sender = sender.substring(2);
      require(sender.matches("01[3-9][0-9]{8}"), "Invalid sender mobile number.");
    }
    return new Captured(method, accountId, provider, sender, reference, snap);
  }
}
