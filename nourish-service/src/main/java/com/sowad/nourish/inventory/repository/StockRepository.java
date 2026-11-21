package com.sowad.nourish.inventory.repository;

import com.sowad.nourish.common.masterdata.MasterDataException;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class StockRepository {
  private final JdbcTemplate jdbc;

  public StockRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public record Product(
      String id, String name, String sku, String kind, String unit, boolean archived) {}

  public record Pack(
      String id,
      String productId,
      BigDecimal amount,
      String unit,
      BigDecimal size,
      String type,
      boolean active) {}

  public record Balance(BigDecimal quantity, long revision) {}

  public Product lockProduct(String id) {
    return jdbc
        .query(
            "SELECT * FROM nourish.product WHERE id=? FOR UPDATE",
            (r, n) ->
                new Product(
                    r.getString("id"),
                    r.getString("name"),
                    r.getString("sku"),
                    r.getString("kind"),
                    r.getString("unit_code"),
                    r.getBoolean("archived")),
            id)
        .stream()
        .findFirst()
        .orElseThrow(() -> MasterDataException.missing("Product"));
  }

  public List<Pack> packs(String id) {
    return jdbc.query(
        "SELECT * FROM nourish.product_packaging WHERE product_id=? ORDER BY id",
        (r, n) ->
            new Pack(
                r.getString("id"),
                id,
                r.getBigDecimal("amount"),
                r.getString("unit_code"),
                r.getBigDecimal("size_base"),
                r.getString("package_type"),
                r.getBoolean("active")),
        id);
  }

  public Balance balance(String id) {
    jdbc.update(
        "INSERT INTO nourish.inventory_balance(product_id) VALUES (?) ON CONFLICT DO NOTHING", id);
    return jdbc.queryForObject(
        "SELECT quantity,revision FROM nourish.inventory_balance WHERE product_id=? FOR UPDATE",
        (r, n) -> new Balance(r.getBigDecimal(1), r.getLong(2)),
        id);
  }

  public long count(String id) {
    Long n =
        jdbc.queryForObject(
            "SELECT coalesce((SELECT package_count FROM nourish.inventory_package_balance WHERE"
                + " packaging_id=?),0)",
            Long.class,
            id);
    return n;
  }

  public void packageCount(Pack p, long count) {
    jdbc.update(
        "INSERT INTO"
            + " nourish.inventory_package_balance(packaging_id,product_id,size_base,package_count)"
            + " VALUES (?,?,?,?) ON CONFLICT(packaging_id) DO UPDATE SET"
            + " package_count=excluded.package_count",
        p.id(),
        p.productId(),
        p.size(),
        count);
  }

  public void balance(String product, BigDecimal after) {
    jdbc.update(
        "UPDATE nourish.inventory_balance SET"
            + " quantity=?,revision=revision+1,updated_at=CURRENT_TIMESTAMP WHERE product_id=?",
        after,
        product);
  }

  public void prices(String product, BigDecimal cost, BigDecimal sale) {
    jdbc.update(
        "INSERT INTO nourish.product_price(product_id,cost_price,sale_price) VALUES (?,?,?) ON"
            + " CONFLICT(product_id) DO UPDATE SET"
            + " cost_price=excluded.cost_price,sale_price=coalesce(excluded.sale_price,nourish.product_price.sale_price),updated_at=CURRENT_TIMESTAMP",
        product,
        cost,
        sale);
  }

  public JdbcTemplate jdbc() {
    return jdbc;
  }
}
