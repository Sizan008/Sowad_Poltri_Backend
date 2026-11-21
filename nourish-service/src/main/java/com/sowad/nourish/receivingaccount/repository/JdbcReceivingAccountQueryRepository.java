package com.sowad.nourish.receivingaccount.repository;

import com.sowad.nourish.common.crypto.AccountProtection;

import com.sowad.nourish.common.masterdata.MasterDataException;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcReceivingAccountQueryRepository {
  private final JdbcTemplate jdbc;
  private final AccountProtection protection;
  private static final String SELECT =
      """
      SELECT id,kind,label,active,provider,wallet_number AS "walletNumber",bank_name AS "bankName",
      account_number AS "accountNumber",account_name AS "accountName"
      FROM nourish.receiving_account
      """;

  public JdbcReceivingAccountQueryRepository(JdbcTemplate jdbc, AccountProtection protection) {
    this.jdbc = jdbc;
    this.protection = protection;
  }

  public Map<String, Object> retrieve(String id) {
    return jdbc.queryForList(SELECT + " WHERE id=?", id).stream().map(this::reveal)
        .findFirst()
        .orElseThrow(() -> MasterDataException.missing("Receiving account"));
  }

  public List<Map<String, Object>> list(
      String q, String kind, Boolean active, Integer limit, int offset) {
    String sql = SELECT + " WHERE true";
    List<Object> args = new ArrayList<>();
    if (kind != null) {
      sql += " AND kind=?";
      args.add(kind);
    }
    if (active != null) {
      sql += " AND active=?";
      args.add(active);
    }
    sql += " ORDER BY lower(label),id";
    String term = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
    var stream = jdbc.queryForList(sql, args.toArray()).stream().map(this::reveal)
        .filter(row -> term.isEmpty() || List.of("label", "bankName", "accountName", "accountNumber", "walletNumber", "provider").stream()
          .anyMatch(field -> row.get(field) != null && row.get(field).toString().toLowerCase(Locale.ROOT).contains(term)))
        .skip(offset);
    return (limit == null ? stream : stream.limit(limit)).toList();
  }
  private Map<String, Object> reveal(Map<String, Object> row) {
    String id = row.get("id").toString();
    row.put("walletNumber", protection.decrypt((String) row.get("walletNumber"), "receiving/" + id + "/wallet"));
    row.put("accountNumber", protection.decrypt((String) row.get("accountNumber"), "receiving/" + id + "/bank"));
    return row;
  }
}
