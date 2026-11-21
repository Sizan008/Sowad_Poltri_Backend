package com.sowad.nourish.contact.repository.entity;

import jakarta.persistence.*;

@MappedSuperclass
public abstract class ContactEntity {
  @Id
  @Column(name = "id")
  private String id;

  @Column(name = "name_en")
  private String nameEn;

  @Column(name = "name_bn")
  private String nameBn;

  @Column(name = "address_en")
  private String addressEn;

  @Column(name = "address_bn")
  private String addressBn;

  @Column(name = "phone")
  private String phone;

  @Column(name = "whatsapp_phone")
  private String whatsappPhone;

  @Column(name = "status")
  private String status;

  @Column(name = "note")
  private String note;

  @Column(name = "archived")
  private boolean archived;

  @Column(name = "created_at")
  private java.time.Instant createdAt;

  @Column(name = "updated_at")
  private java.time.Instant updatedAt;

  @Version private long revision;

  public String getId() {
    return id;
  }

  public void setId(String value) {
    this.id = value;
  }

  public String getNameEn() {
    return nameEn;
  }

  public void setNameEn(String value) {
    this.nameEn = value;
  }

  public String getNameBn() {
    return nameBn;
  }

  public void setNameBn(String value) {
    this.nameBn = value;
  }

  public String getAddressEn() {
    return addressEn;
  }

  public void setAddressEn(String value) {
    this.addressEn = value;
  }

  public String getAddressBn() {
    return addressBn;
  }

  public void setAddressBn(String value) {
    this.addressBn = value;
  }

  public String getPhone() {
    return phone;
  }

  public void setPhone(String value) {
    this.phone = value;
  }

  public String getWhatsappPhone() {
    return whatsappPhone;
  }

  public void setWhatsappPhone(String value) {
    this.whatsappPhone = value;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String value) {
    this.status = value;
  }

  public String getNote() {
    return note;
  }

  public void setNote(String value) {
    this.note = value;
  }

  public boolean getArchived() {
    return archived;
  }

  public void setArchived(boolean value) {
    this.archived = value;
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
