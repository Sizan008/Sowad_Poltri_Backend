package com.sowad.nourish.health.repository;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.Map;

@Repository
public class JdbcHealthQueryRepository implements HealthQueryRepository {
    private static final String HEALTH_SQL = """
            SELECT current_database() AS database_name,
                   current_schema() AS schema_name,
                   COALESCE(MAX(version), 0) AS schema_version
            FROM nourish.schema_version
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcHealthQueryRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public DatabaseState retrieveDatabaseState() {
        return jdbcTemplate.queryForObject(
                HEALTH_SQL,
                Map.of(),
                (result, row) -> new DatabaseState(
                        result.getString("database_name"),
                        result.getString("schema_name"),
                        result.getInt("schema_version")));
    }
}
