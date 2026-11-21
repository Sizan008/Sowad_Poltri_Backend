package com.sowad.nourish.customerrate.repository.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "customer_product_rate", schema = "nourish")
public class CustomerProductRateEntity {
  @Id
  @Column(name = "id")
  private String id;

  @Column(name = "customer_id")
  private String customerId;

  @Column(name = "product_id")
  private String productId;

  @Column(name = "price_per_unit")
  private java.math.BigDecimal pricePerUnit;

  @Version private long revision;

  public String getId() {
    return id;
  }

  public void setId(String value) {
    this.id = value;
  }

  public String getCustomerId() {
    return customerId;
  }

  public void setCustomerId(String value) {
    this.customerId = value;
  }

  public String getProductId() {
    return productId;
  }

  public void setProductId(String value) {
    this.productId = value;
  }

  public java.math.BigDecimal getPricePerUnit() {
    return pricePerUnit;
  }

  public void setPricePerUnit(java.math.BigDecimal value) {
    this.pricePerUnit = value;
  }
}
