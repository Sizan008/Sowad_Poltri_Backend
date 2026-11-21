package com.sowad.nourish.activitylog.repository.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "activity_log", schema = "nourish")
public class ActivityLogEntity {
    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "actor_id", length = 64)
    private String actorId;

    @Column(name = "actor_name", nullable = false, length = 120)
    private String actorName;

    @Column(nullable = false, length = 20)
    private String role;

    @Column(nullable = false, length = 50)
    private String action;

    @Column(nullable = false, length = 50)
    private String entity;

    @Column(name = "entity_id", length = 64)
    private String entityId;

    @Column(nullable = false, length = 500)
    private String details;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    public ActivityLogEntity() {}

    public String getId() {
        return id;
    }

    public void setId(String value) {
        this.id = value;
    }

    public String getActorId() {
        return actorId;
    }

    public void setActorId(String value) {
        this.actorId = value;
    }

    public String getActorName() {
        return actorName;
    }

    public void setActorName(String value) {
        this.actorName = value;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String value) {
        this.role = value;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String value) {
        this.action = value;
    }

    public String getEntity() {
        return entity;
    }

    public void setEntity(String value) {
        this.entity = value;
    }

    public String getEntityId() {
        return entityId;
    }

    public void setEntityId(String value) {
        this.entityId = value;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String value) {
        this.details = value;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant value) {
        this.occurredAt = value;
    }
}
