package com.sowad.nourish.inventory;

import com.sowad.nourish.common.masterdata.MasterDataException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class StockPolicyImpl implements StockPolicy {
  private final JdbcTemplate jdbc;

  public StockPolicyImpl(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void requireEmptyProduct(String id) {
    if (Boolean.TRUE.equals(
        jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM nourish.inventory_balance WHERE product_id=? AND"
                + " quantity<>0)",
            Boolean.class,
            id)))
      throw MasterDataException.conflict(
          "Cannot archive a product with stock. Adjust/reconcile its stock first.");
  }

  public void requireEmptyPackaging(String id) {
    if (Boolean.TRUE.equals(
        jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM nourish.inventory_package_balance WHERE packaging_id=? AND"
                + " package_count<>0)",
            Boolean.class,
            id))) throw MasterDataException.conflict("Cannot deactivate packaging with stock.");
  }
}
