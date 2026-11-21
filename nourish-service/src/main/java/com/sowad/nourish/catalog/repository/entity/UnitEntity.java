package com.sowad.nourish.catalog.repository.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "product_unit", schema = "nourish")
public class UnitEntity {
  @Id
  @Column(name = "id")
  private String id;

  @Column(name = "code")
  private String code;

  @Column(name = "name")
  private String name;

  @Column(name = "symbol")
  private String symbol;

  @Column(name = "allow_decimal")
  private boolean allowDecimal;

  @Column(name = "system_unit")
  private boolean systemUnit;

  @Version private long revision;

  public String getId() {
    return id;
  }

  public void setId(String value) {
    this.id = value;
  }

  public String getCode() {
    return code;
  }

  public void setCode(String value) {
    this.code = value;
  }

  public String getName() {
    return name;
  }

  public void setName(String value) {
    this.name = value;
  }

  public String getSymbol() {
    return symbol;
  }

  public void setSymbol(String value) {
    this.symbol = value;
  }

  public boolean getAllowDecimal() {
    return allowDecimal;
  }

  public void setAllowDecimal(boolean value) {
    this.allowDecimal = value;
  }

  public boolean getSystemUnit() {
    return systemUnit;
  }

  public void setSystemUnit(boolean value) {
    this.systemUnit = value;
  }
}
