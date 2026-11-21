package com.sowad.nourish.settings.repository.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "business_settings", schema = "nourish")
public class BusinessSettingsEntity {
    @Id
    @Column(length = 20)
    private String id;

    @Column(name = "business_name", nullable = false, length = 200)
    private String businessName;

    @Column(name = "owner_name", nullable = false, length = 120)
    private String ownerName;

    @Column(nullable = false, length = 40)
    private String phone;

    @Column(nullable = false, length = 200)
    private String email;

    @Column(nullable = false, length = 1000)
    private String address;

    @Column(name = "invoice_footer", nullable = false, length = 500)
    private String invoiceFooter;

    @Column(name = "low_stock_default", nullable = false, precision = 10, scale = 3)
    private BigDecimal lowStockDefault;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "ui_language", nullable = false, length = 2)
    private String uiLanguage;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", length = 64)
    private String updatedBy;

    @Version
    @Column(nullable = false)
    private long revision;

    public BusinessSettingsEntity() {}

    public String getId() {
        return id;
    }

    public void setId(String value) {
        this.id = value;
    }

    public String getBusinessName() {
        return businessName;
    }

    public void setBusinessName(String value) {
        this.businessName = value;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String value) {
        this.ownerName = value;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String value) {
        this.phone = value;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String value) {
        this.email = value;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String value) {
        this.address = value;
    }

    public String getInvoiceFooter() {
        return invoiceFooter;
    }

    public void setInvoiceFooter(String value) {
        this.invoiceFooter = value;
    }

    public BigDecimal getLowStockDefault() {
        return lowStockDefault;
    }

    public void setLowStockDefault(BigDecimal value) {
        this.lowStockDefault = value;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String value) {
        this.currency = value;
    }

    public String getUiLanguage() {
        return uiLanguage;
    }

    public void setUiLanguage(String value) {
        this.uiLanguage = value;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant value) {
        this.updatedAt = value;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String value) {
        this.updatedBy = value;
    }

    public long getRevision() {
        return revision;
    }

    public void setRevision(long value) {
        this.revision = value;
    }
}
