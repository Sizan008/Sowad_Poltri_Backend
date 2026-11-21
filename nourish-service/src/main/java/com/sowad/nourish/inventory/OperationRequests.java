package com.sowad.nourish.inventory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sowad.nourish.common.masterdata.MasterDataException;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class OperationRequests {
  private final JdbcTemplate jdbc;
  private final ObjectMapper json;

  public OperationRequests(JdbcTemplate jdbc, ObjectMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  public String hash(Object command) {
    try {
      return HexFormat.of()
          .formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(command)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  public String begin(String key, String type, String hash) {
    StockNumbers.require(
        key != null && key.matches("[A-Za-z0-9_-]{8,80}"),
        "Idempotency-Key must contain 8-80 letters, digits, hyphens or underscores.");
    jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,76201905))", key);
    var rows =
        jdbc.queryForList(
            "SELECT operation_type,body_hash,result_id FROM nourish.stock_request WHERE"
                + " request_key=?",
            key);
    if (rows.isEmpty()) return null;
    var row = rows.getFirst();
    if (!type.equals(row.get("operation_type")) || !hash.equals(row.get("body_hash")))
      throw MasterDataException.conflict("Idempotency-Key already belongs to another request.");
    return row.get("result_id").toString();
  }

  public void finish(String key, String type, String hash, String id) {
    jdbc.update(
        "INSERT INTO nourish.stock_request(request_key,operation_type,body_hash,result_id) VALUES"
            + " (?,?,?,?)",
        key,
        type,
        hash,
        id);
  }
}
