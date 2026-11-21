package com.sowad.nourish.user.repository;

import com.sowad.nourish.common.security.CurrentUser;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.*;

@Repository
public class JdbcUserQueryRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public JdbcUserQueryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<CurrentUser> retrieveUsers(int limit, int offset) {
        return jdbc.query(
                """
                SELECT u.id,u.username,u.display_name,
                       string_agg(r.role_code, ',' ORDER BY r.role_code) AS roles
                FROM nourish.app_user u JOIN nourish.app_user_role r ON r.user_id=u.id
                GROUP BY u.id ORDER BY u.username LIMIT :limit OFFSET :offset
                """,
                Map.of("limit", limit, "offset", offset),
                (rs, n) -> {
                    var roles = Set.copyOf(Arrays.asList(rs.getString("roles").split(",")));
                    String primary =
                            roles.contains("admin")
                                    ? "admin"
                                    : roles.contains("operator") ? "operator" : "viewer";
                    return new CurrentUser(
                            rs.getString("id"),
                            rs.getString("username"),
                            rs.getString("display_name"),
                            primary,
                            roles);
                });
    }
}
