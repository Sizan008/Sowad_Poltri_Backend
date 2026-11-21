package com.sowad.nourish.health.repository;

public interface HealthQueryRepository {
    DatabaseState retrieveDatabaseState();

    record DatabaseState(String database, String schema, int version) {
    }
}
