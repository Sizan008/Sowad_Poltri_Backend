package com.sowad.nourish.purchase;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sowad.nourish.common.masterdata.MasterDataException;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@PreAuthorize("hasAnyRole('admin','operator','viewer')")
public class PurchaseQueries {
  private final JdbcTemplate jdbc;
  private final ObjectMapper json;

  public PurchaseQueries(JdbcTemplate jdbc, ObjectMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  private static final String HEADER =
      "SELECT id,purchase_number AS \"purchaseNumber\",supplier_id AS \"supplierId\",supplier_name"
          + " AS \"supplierName\",purchased_at AS \"purchasedAt\",total_amount AS"
          + " \"totalAmount\",reference,notes,created_at AS \"createdAt\" FROM nourish.purchase";

  public List<Map<String, Object>> list(String supplier, int limit, int offset) {
    return jdbc.queryForList(
        HEADER
            + " WHERE (?::text IS NULL OR supplier_id=?) ORDER BY purchased_at DESC,id DESC LIMIT ?"
            + " OFFSET ?",
        supplier,
        supplier,
        limit,
        offset);
  }

  public Map<String, Object> retrieve(String id) {
    var rows = jdbc.queryForList(HEADER + " WHERE id=?", id);
    if (rows.isEmpty()) throw MasterDataException.missing("Purchase");
    var row = rows.getFirst();
    row.put("items", items(id));
    return row;
  }

  public List<Map<String, Object>> items(String id) {
    var rows =
        jdbc.queryForList(
            "SELECT id,purchase_id AS \"purchaseId\",product_id AS \"productId\",product_name AS"
                + " \"productName\",sku,unit_code AS unit,quantity,unit_price AS"
                + " \"unitPrice\",line_total AS \"lineTotal\",'purchase-snapshot' AS"
                + " \"pricingBasis\",pricing_snapshot::text AS snapshot FROM nourish.purchase_item"
                + " WHERE purchase_id=? ORDER BY line_number",
            id);
    for (var row : rows) {
      try {
        row.put(
            "purchasePricingSnapshot", json.readValue((String) row.remove("snapshot"), Map.class));
      } catch (Exception e) {
        throw new IllegalStateException(e);
      }
    }
    for (var row : rows) {
      var details =
          jdbc.queryForList(
              "SELECT d.size_base AS \"sizeBase\",d.package_count AS count,p.amount,p.unit_code AS"
                  + " \"unitCode\" FROM nourish.purchase_package_detail d LEFT JOIN"
                  + " nourish.product_packaging p ON p.id=d.packaging_id WHERE d.purchase_item_id=?"
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
}
