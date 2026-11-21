package com.sowad.nourish.authentication.repository.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "auth_session", schema = "nourish")
public class SessionEntity {
    @Id
    @Column(name = "token_hash", length = 64)
    private String tokenHash;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    private com.sowad.nourish.user.repository.entity.UserEntity user;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    public SessionEntity() {}

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String value) {
        this.tokenHash = value;
    }

    public com.sowad.nourish.user.repository.entity.UserEntity getUser() {
        return user;
    }

    public void setUser(com.sowad.nourish.user.repository.entity.UserEntity value) {
        this.user = value;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant value) {
        this.createdAt = value;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant value) {
        this.expiresAt = value;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(Instant value) {
        this.revokedAt = value;
    }
}
