package com.sowad.nourish.catalog.repository;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcCatalogQueryRepository {
  private final JdbcTemplate jdbc;

  public JdbcCatalogQueryRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<Map<String, Object>> categories() {
    return jdbc.queryForList(
        "SELECT id,name,kind,description,false AS archived FROM nourish.category ORDER BY"
            + " sort_order");
  }

  public List<Map<String, Object>> units() {
    return jdbc.queryForList(
        "SELECT id,code,name,symbol,allow_decimal AS \"allowDecimal\" FROM nourish.product_unit"
            + " ORDER BY code");
  }

  public String kind(String id) {
    return jdbc
        .query("SELECT kind FROM nourish.category WHERE id=?", (r, n) -> r.getString(1), id)
        .stream()
        .findFirst()
        .orElseThrow(() -> new com.sowad.nourish.catalog.CatalogFailure(400, "Invalid category."));
  }

  public List<Map<String, Object>> products() {
    return jdbc.queryForList(
        """
SELECT p.id,p.name,p.sku,p.category_id AS "categoryId",p.kind,p.unit_code AS unit,
p.subtype,p.grade,p.manufacturer,p.medicine_form AS "medicineForm",p.feed_group AS "feedGroup",
p.feed_type AS "feedType",p.feed_catalog_id AS "feedCatalogId",p.archived,
p.low_stock_threshold AS "lowStockThreshold",coalesce(b.quantity,0) AS "stockQuantity",coalesce(pr.cost_price,0) AS "costPrice",coalesce(pr.sale_price,0) AS "salePrice",
CASE WHEN p.feed_catalog_id IS NULL THEN 'manual-v14' ELSE NULL END AS "registrationSource",f.product_code AS "productCode",f.bag_size_kg AS "defaultBagSizeKg",
f.agent_price_per_kg AS "referenceAgentPriceKg",f.mrp_per_kg AS "referenceMrpKg",f.mrp_per_bag AS "referenceMrpBag",
to_char(f.effective_date,'YYYY-MM-DD') AS "referencePriceDate",true AS "referencePricesUnverified"
FROM nourish.product p LEFT JOIN nourish.feed_price_catalog f ON p.feed_catalog_id=f.id LEFT JOIN nourish.inventory_balance b ON b.product_id=p.id LEFT JOIN nourish.product_price pr ON pr.product_id=p.id ORDER BY p.name,p.id
""");
  }

  public List<Map<String, Object>> feed() {
    return jdbc.queryForList(
        """
SELECT id,product_code AS "productCode",name,feed_group AS "group",feed_type AS "feedType",
bag_size_kg AS "bagSizeKg",agent_price_per_kg AS "agentPricePerKg",mrp_per_kg AS "mrpPerKg",
mrp_per_bag AS "mrpPerBag",to_char(effective_date,'YYYY-MM-DD') AS "effectiveDate",source
FROM nourish.feed_price_catalog ORDER BY product_code
""");
  }

  public List<Map<String, Object>> packaging(String productId) {
    return jdbc.queryForList(
        """
SELECT id,product_id AS "productId",amount,unit_code AS "unitCode",size_base AS "sizeBase",package_type AS "packageType",active
FROM nourish.product_packaging WHERE product_id=? ORDER BY size_base,id
""",
        productId);
  }

  public String options() {
    return jdbc.queryForObject(
        "SELECT options::text FROM nourish.catalog_options WHERE id='product-types'", String.class);
  }
}
