package com.sowad.nourish.settings.repository;

import com.sowad.nourish.settings.query.BusinessSettings;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Map;

@Repository
public class JdbcBusinessSettingsQueryRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public JdbcBusinessSettingsQueryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public BusinessSettings retrieveSettings() {
        return jdbc.queryForObject(
                "SELECT * FROM nourish.business_settings WHERE id=:id",
                Map.of("id", "business"),
                (rs, n) ->
                        new BusinessSettings(
                                rs.getString("id"),
                                rs.getString("business_name"),
                                rs.getString("owner_name"),
                                rs.getString("phone"),
                                rs.getString("email"),
                                rs.getString("address"),
                                rs.getString("invoice_footer"),
                                rs.getBigDecimal("low_stock_default"),
                                rs.getString("currency"),
                                rs.getString("ui_language")));
    }
}
