package com.sowad.nourish.receivingaccount.repository.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "receiving_account", schema = "nourish")
public class ReceivingAccountEntity {
  @Id
  @Column(name = "id")
  private String id;

  @Column(name = "kind")
  private String kind;

  @Column(name = "label")
  private String label;

  @Column(name = "active")
  private boolean active;

  @Column(name = "provider")
  private String provider;

  @Column(name = "wallet_number", length = 512)
  private String walletNumber;

  @Column(name = "bank_name")
  private String bankName;

  @Column(name = "account_number", length = 512)
  private String accountNumber;

  @Column(name = "account_name")
  private String accountName;

  @Column(name = "created_at")
  private java.time.Instant createdAt;

  @Column(name = "updated_at")
  private java.time.Instant updatedAt;

  @Column(name = "wallet_number_lookup", length = 64)
  private String walletNumberLookup;
  @Column(name = "account_number_lookup", length = 64)
  private String accountNumberLookup;
  public void setWalletNumberLookup(String value) { walletNumberLookup = value; }
  public void setAccountNumberLookup(String value) { accountNumberLookup = value; }
  @Version private long revision;

  public String getId() {
    return id;
  }

  public void setId(String value) {
    this.id = value;
  }

  public String getKind() {
    return kind;
  }

  public void setKind(String value) {
    this.kind = value;
  }

  public String getLabel() {
    return label;
  }

  public void setLabel(String value) {
    this.label = value;
  }

  public boolean getActive() {
    return active;
  }

  public void setActive(boolean value) {
    this.active = value;
  }

  public String getProvider() {
    return provider;
  }

  public void setProvider(String value) {
    this.provider = value;
  }

  public String getWalletNumber() {
    return walletNumber;
  }

  public void setWalletNumber(String value) {
    this.walletNumber = value;
  }

  public String getBankName() {
    return bankName;
  }

  public void setBankName(String value) {
    this.bankName = value;
  }

  public String getAccountNumber() {
    return accountNumber;
  }

  public void setAccountNumber(String value) {
    this.accountNumber = value;
  }

  public String getAccountName() {
    return accountName;
  }

  public void setAccountName(String value) {
    this.accountName = value;
  }

  public java.time.Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(java.time.Instant value) {
    this.createdAt = value;
  }

  public java.time.Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(java.time.Instant value) {
    this.updatedAt = value;
  }
}
