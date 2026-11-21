package com.sowad.nourish.contact.repository;

import com.sowad.nourish.common.masterdata.MasterDataException;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcContactQueryRepository {
  public enum Type {
    CUSTOMER("customer"),
    SUPPLIER("supplier");
    final String table;

    Type(String table) {
      this.table = table;
    }
  }

  private final JdbcTemplate jdbc;

  public JdbcContactQueryRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  private String select(Type type) {
    return """
SELECT id,name_en AS name,name_en AS "nameEn",name_bn AS "nameBn",address_en AS area,
address_en AS "addressEn",address_bn AS "addressBn",phone,whatsapp_phone AS "whatsappPhone",
status,note,note AS "lastNote",archived,created_at AS "createdAt",updated_at AS "updatedAt"
FROM nourish.
"""
            .stripTrailing()
        + type.table;
  }

  public List<Map<String, Object>> list(
      Type type, String q, String status, Boolean archived, Integer limit, int offset) {
    String sql = select(type) + " WHERE archived=?";
    List<Object> args = new ArrayList<>();
    args.add(Boolean.TRUE.equals(archived));
    if (status != null) {
      sql += " AND status=?";
      args.add(status);
    }
    if (q != null && !q.isBlank()) {
      sql +=
          " AND (name_en ILIKE ? ESCAPE '!' OR name_bn ILIKE ? ESCAPE '!' OR address_en ILIKE ?"
              + " ESCAPE '!' OR address_bn ILIKE ? ESCAPE '!' OR phone ILIKE ? ESCAPE '!' OR"
              + " whatsapp_phone ILIKE ? ESCAPE '!')";
      String term = "%" + q.trim().replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
      for (int i = 0; i < 6; i++) args.add(term);
    }
    sql += " ORDER BY lower(name_en),id";
    if (limit != null) {
      sql += " LIMIT ?";
      args.add(limit);
    }
    sql += " OFFSET ?";
    args.add(offset);
    return normalize(jdbc.queryForList(sql, args.toArray()));
  }

  public Map<String, Object> retrieve(Type type, String id) {
    return normalize(jdbc.queryForList(select(type) + " WHERE id=?", id)).stream()
        .findFirst()
        .orElseThrow(() -> MasterDataException.missing(type.table));
  }

  private List<Map<String, Object>> normalize(List<Map<String, Object>> rows) {
    for (var row : rows)
      for (String key : List.of("createdAt", "updatedAt")) {
        Object value = row.get(key);
        if (value instanceof java.sql.Timestamp stamp) row.put(key, stamp.toInstant().toString());
      }
    return rows;
  }
}
