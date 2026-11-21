package com.sowad.nourish.health.query;

public record HealthDetails(
        String status,
        int phase,
        String mode,
        String database,
        String schema,
        int schemaVersion) {
}
