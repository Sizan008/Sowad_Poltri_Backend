package com.sowad.nourish.health.repository.entity;

import java.time.Instant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "schema_version", schema = "nourish")
public class SchemaVersionEntity {
    @Id
    private Integer version;

    @Column(nullable = false, length = 200)
    private String description;

    @Column(name = "installed_at", nullable = false)
    private Instant installedAt;

    protected SchemaVersionEntity() {
    }

    public Integer getVersion() { return version; }
    public String getDescription() { return description; }
    public Instant getInstalledAt() { return installedAt; }
}
