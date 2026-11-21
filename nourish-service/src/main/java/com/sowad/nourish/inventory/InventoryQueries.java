package com.sowad.nourish.inventory;

import com.sowad.nourish.common.masterdata.MasterDataException;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@PreAuthorize("hasAnyRole('admin','operator','viewer')")
public class InventoryQueries {
  private final JdbcTemplate jdbc;

  public InventoryQueries(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<Map<String, Object>> list(
      String product, String kind, boolean low, int limit, int offset) {
    return jdbc.queryForList(
        """
SELECT p.id AS "productId",p.name,p.kind,p.unit_code AS unit,p.archived,
coalesce(b.quantity,0) AS "stockQuantity",coalesce(b.revision,0) AS revision,
p.low_stock_threshold AS "lowStockThreshold",
coalesce(b.quantity,0)<=p.low_stock_threshold AS "lowStock"
FROM nourish.product p LEFT JOIN nourish.inventory_balance b ON b.product_id=p.id
WHERE (?::text IS NULL OR p.id=?) AND (?::text IS NULL OR p.kind=?)
AND (NOT ? OR (NOT p.archived AND coalesce(b.quantity,0)<=p.low_stock_threshold))
ORDER BY p.name,p.id LIMIT ? OFFSET ?
""",
        product,
        product,
        kind,
        kind,
        low,
        limit,
        offset);
  }

  public Map<String, Object> retrieve(String id) {
    var rows = list(id, null, false, 1, 0);
    if (rows.isEmpty()) throw MasterDataException.missing("Product");
    var row = rows.getFirst();
    row.put("packages", packages(id, null, 200, 0));
    return row;
  }

  public List<Map<String, Object>> packages(String product, String kind, int limit, int offset) {
    return jdbc.queryForList(
        """
SELECT d.id,d.id AS "packagingId",d.product_id AS "productId",p.kind,d.amount,
d.unit_code AS "unitCode",d.size_base AS "sizeBase",d.package_type AS "packageType",d.active,
coalesce(b.package_count,0) AS "packageCount",coalesce(b.quantity,0) AS quantity,
CASE WHEN p.kind='feed' THEN d.size_base END AS "bagSizeKg",
CASE WHEN p.kind='feed' THEN coalesce(b.package_count,0) END AS "bagCount",
CASE WHEN p.kind='medicine' THEN coalesce(b.package_count,0) END AS "packCount"
FROM nourish.product_packaging d JOIN nourish.product p ON p.id=d.product_id
LEFT JOIN nourish.inventory_package_balance b ON b.packaging_id=d.id
WHERE (?::text IS NULL OR p.id=?) AND (?::text IS NULL OR p.kind=?)
ORDER BY p.id,d.size_base,d.id LIMIT ? OFFSET ?
""",
        product,
        product,
        kind,
        kind,
        limit,
        offset);
  }

  public List<Map<String, Object>> movements(String product, String type, int limit, int offset) {
    return jdbc.queryForList(
        """
SELECT id,product_id AS "productId",movement_type AS type,related_id AS "relatedId",
quantity_change AS "quantityChange",stock_before AS "stockBefore",stock_after AS "stockAfter",
stock_revision AS revision,occurred_at AS "occurredAt",created_at AS "createdAt",reason,actor
FROM nourish.stock_movement WHERE (?::text IS NULL OR product_id=?) AND (?::text IS NULL OR movement_type=?)
ORDER BY created_at DESC,product_id,stock_revision DESC LIMIT ? OFFSET ?
""",
        product,
        product,
        type,
        type,
        limit,
        offset);
  }

  public Map<String, Object> movement(String id) {
    var rows = jdbc.queryForList("SELECT * FROM nourish.stock_movement WHERE id=?", id);
    if (rows.isEmpty()) throw MasterDataException.missing("Movement");
    var row = rows.getFirst();
    row.put(
        "packages",
        jdbc.queryForList(
            "SELECT packaging_id AS \"packagingId\",size_base AS \"sizeBase\",count_before AS"
                + " \"countBefore\",count_after AS \"countAfter\" FROM"
                + " nourish.stock_movement_package WHERE movement_id=? ORDER BY packaging_id",
            id));
    return row;
  }

  public Map<String, Object> adjustment(String id) {
    var rows =
        jdbc.queryForList(
            "SELECT id,product_id AS \"productId\",mode,reason,expected_revision AS"
                + " \"expectedRevision\",created_at AS \"createdAt\" FROM nourish.stock_adjustment"
                + " WHERE id=?",
            id);
    if (rows.isEmpty()) throw MasterDataException.missing("Adjustment");
    var row = rows.getFirst();
    String movement =
        jdbc.queryForObject(
            "SELECT id FROM nourish.stock_movement WHERE related_id=? AND movement_type IN"
                + " ('adjustment','recount')",
            String.class,
            id);
    row.put("movement", movement(movement));
    return row;
  }
}
