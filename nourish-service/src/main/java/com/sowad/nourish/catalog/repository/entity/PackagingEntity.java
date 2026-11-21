package com.sowad.nourish.catalog.repository.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "product_packaging", schema = "nourish")
public class PackagingEntity {
  @Id
  @Column(name = "id")
  private String id;

  @Column(name = "product_id")
  private String productId;

  @Column(name = "amount")
  private java.math.BigDecimal amount;

  @Column(name = "unit_code")
  private String unitCode;

  @Column(name = "size_base")
  private java.math.BigDecimal sizeBase;

  @Column(name = "package_type")
  private String packageType;

  @Column(name = "active")
  private boolean active;

  @Version private long revision;

  public String getId() {
    return id;
  }

  public void setId(String value) {
    this.id = value;
  }

  public String getProductId() {
    return productId;
  }

  public void setProductId(String value) {
    this.productId = value;
  }

  public java.math.BigDecimal getAmount() {
    return amount;
  }

  public void setAmount(java.math.BigDecimal value) {
    this.amount = value;
  }

  public String getUnitCode() {
    return unitCode;
  }

  public void setUnitCode(String value) {
    this.unitCode = value;
  }

  public java.math.BigDecimal getSizeBase() {
    return sizeBase;
  }

  public void setSizeBase(java.math.BigDecimal value) {
    this.sizeBase = value;
  }

  public String getPackageType() {
    return packageType;
  }

  public void setPackageType(String value) {
    this.packageType = value;
  }

  public boolean getActive() {
    return active;
  }

  public void setActive(boolean value) {
    this.active = value;
  }
}
