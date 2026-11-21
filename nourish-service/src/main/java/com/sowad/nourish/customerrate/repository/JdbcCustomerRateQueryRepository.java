package com.sowad.nourish.customerrate.repository;

import java.util.*;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcCustomerRateQueryRepository {
  private final NamedParameterJdbcTemplate jdbc;

  public JdbcCustomerRateQueryRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<Map<String, Object>> unitRates(Collection<String> ids) {
    if (ids.isEmpty()) return List.of();
    return jdbc.queryForList(
        """
SELECT r.customer_id AS "customerId",r.product_id AS "productId",r.price_per_unit AS "pricePerUnit",
p.unit_code AS unit,CASE WHEN p.kind='feed' THEN r.price_per_unit ELSE NULL END AS "pricePerKg",p.archived AS "productArchived"
FROM nourish.customer_product_rate r JOIN nourish.product p ON p.id=r.product_id
WHERE r.customer_id IN (:ids) ORDER BY r.product_id
""",
        Map.of("ids", ids));
  }

  public List<Map<String, Object>> packageRates(Collection<String> ids) {
    if (ids.isEmpty()) return List.of();
    return jdbc.queryForList(
        """
SELECT r.customer_id AS "customerId",r.product_id AS "productId",r.packaging_id AS "packagingId",
r.price_per_package AS "pricePerPackage",p.unit_code AS unit,p.archived AS "productArchived",
pack.active AS "packagingActive",pack.amount,pack.unit_code AS "unitCode",pack.size_base AS "sizeBase"
FROM nourish.customer_package_rate r JOIN nourish.product p ON p.id=r.product_id
JOIN nourish.product_packaging pack ON pack.id=r.packaging_id
WHERE r.customer_id IN (:ids) ORDER BY r.product_id,r.packaging_id
""",
        Map.of("ids", ids));
  }
}
