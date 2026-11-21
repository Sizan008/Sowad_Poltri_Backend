package com.sowad.nourish.user;

import com.sowad.nourish.common.security.CurrentUser;
import com.sowad.nourish.user.repository.entity.UserEntity;

import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
public class UserMapper {
    public CurrentUser toCurrentUser(UserEntity entity) {
        var roles =
                entity.getRoles().stream().map(role -> role.getCode()).collect(Collectors.toSet());
        String primary =
                roles.contains("admin")
                        ? "admin"
                        : roles.contains("operator") ? "operator" : "viewer";
        return new CurrentUser(
                entity.getId(), entity.getUsername(), entity.getDisplayName(), primary, roles);
    }
}
