package com.sowad.nourish.activitylog.query;

import java.time.Instant;

public record ActivityLog(
        String id,
        String actorId,
        String actorName,
        String role,
        String action,
        String entity,
        String entityId,
        String details,
        Instant occurredAt) {}
