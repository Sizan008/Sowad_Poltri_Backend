package com.sowad.nourish.customerrate.repository.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "customer_package_rate", schema = "nourish")
public class CustomerPackageRateEntity {
  @Id
  @Column(name = "id")
  private String id;

  @Column(name = "customer_id")
  private String customerId;

  @Column(name = "product_id")
  private String productId;

  @Column(name = "packaging_id")
  private String packagingId;

  @Column(name = "price_per_package")
  private java.math.BigDecimal pricePerPackage;

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

  public String getPackagingId() {
    return packagingId;
  }

  public void setPackagingId(String value) {
    this.packagingId = value;
  }

  public java.math.BigDecimal getPricePerPackage() {
    return pricePerPackage;
  }

  public void setPricePerPackage(java.math.BigDecimal value) {
    this.pricePerPackage = value;
  }
}
