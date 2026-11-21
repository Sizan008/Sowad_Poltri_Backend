package com.sowad.nourish.user.repository.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "app_user", schema = "nourish")
public class UserEntity {
    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false, unique = true, length = 64)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "app_user_role",
            schema = "nourish",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_code"))
    private Set<RoleEntity> roles;

    public UserEntity() {}

    public String getId() {
        return id;
    }

    public void setId(String value) {
        this.id = value;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String value) {
        this.username = value;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String value) {
        this.passwordHash = value;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String value) {
        this.displayName = value;
    }

    public boolean getActive() {
        return active;
    }

    public void setActive(boolean value) {
        this.active = value;
    }

    public int getFailedAttempts() {
        return failedAttempts;
    }

    public void setFailedAttempts(int value) {
        this.failedAttempts = value;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public void setLockedUntil(Instant value) {
        this.lockedUntil = value;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant value) {
        this.createdAt = value;
    }

    public Set<RoleEntity> getRoles() {
        return roles;
    }

    public void setRoles(Set<RoleEntity> value) {
        this.roles = value;
    }
}
