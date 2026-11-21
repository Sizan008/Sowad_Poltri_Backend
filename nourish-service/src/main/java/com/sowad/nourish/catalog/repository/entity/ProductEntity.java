package com.sowad.nourish.catalog.repository.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "product", schema = "nourish")
public class ProductEntity {
  @Id
  @Column(name = "id")
  private String id;

  @Column(name = "name")
  private String name;

  @Column(name = "sku")
  private String sku;

  @Column(name = "category_id")
  private String categoryId;

  @Column(name = "kind")
  private String kind;

  @Column(name = "unit_code")
  private String unit;

  @Column(name = "subtype")
  private String subtype;

  @Column(name = "grade")
  private String grade;

  @Column(name = "manufacturer")
  private String manufacturer;

  @Column(name = "medicine_form")
  private String medicineForm;

  @Column(name = "feed_group")
  private String feedGroup;

  @Column(name = "feed_type")
  private String feedType;

  @Column(name = "feed_catalog_id")
  private String feedCatalogId;

  @Column(name = "archived")
  private boolean archived;

  @Column(name = "low_stock_threshold")
  private java.math.BigDecimal lowStockThreshold;

  @Version private long revision;

  public String getId() {
    return id;
  }

  public void setId(String value) {
    this.id = value;
  }

  public String getName() {
    return name;
  }

  public void setName(String value) {
    this.name = value;
  }

  public String getSku() {
    return sku;
  }

  public void setSku(String value) {
    this.sku = value;
  }

  public String getCategoryId() {
    return categoryId;
  }

  public void setCategoryId(String value) {
    this.categoryId = value;
  }

  public String getKind() {
    return kind;
  }

  public void setKind(String value) {
    this.kind = value;
  }

  public String getUnit() {
    return unit;
  }

  public void setUnit(String value) {
    this.unit = value;
  }

  public String getSubtype() {
    return subtype;
  }

  public void setSubtype(String value) {
    this.subtype = value;
  }

  public String getGrade() {
    return grade;
  }

  public void setGrade(String value) {
    this.grade = value;
  }

  public String getManufacturer() {
    return manufacturer;
  }

  public void setManufacturer(String value) {
    this.manufacturer = value;
  }

  public String getMedicineForm() {
    return medicineForm;
  }

  public void setMedicineForm(String value) {
    this.medicineForm = value;
  }

  public String getFeedGroup() {
    return feedGroup;
  }

  public void setFeedGroup(String value) {
    this.feedGroup = value;
  }

  public String getFeedType() {
    return feedType;
  }

  public void setFeedType(String value) {
    this.feedType = value;
  }

  public String getFeedCatalogId() {
    return feedCatalogId;
  }

  public void setFeedCatalogId(String value) {
    this.feedCatalogId = value;
  }

  public boolean getArchived() {
    return archived;
  }

  public void setArchived(boolean value) {
    this.archived = value;
  }

  public java.math.BigDecimal getLowStockThreshold() {
    return lowStockThreshold;
  }

  public void setLowStockThreshold(java.math.BigDecimal value) {
    this.lowStockThreshold = value;
  }
}
