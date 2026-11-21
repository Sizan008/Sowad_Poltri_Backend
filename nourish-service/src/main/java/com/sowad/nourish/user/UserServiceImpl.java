package com.sowad.nourish.user;

import com.sowad.nourish.activitylog.ActivityLogService;
import com.sowad.nourish.common.exception.BusinessException;
import com.sowad.nourish.common.security.*;
import com.sowad.nourish.user.command.CreateUser;
import com.sowad.nourish.user.repository.*;
import com.sowad.nourish.user.repository.entity.UserEntity;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

@Service
public class UserServiceImpl implements UserService {
    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder encoder;
    private final UserMapper mapper;
    private final CurrentUserProvider currentUser;
    private final ActivityLogService audit;
    private final JdbcTemplate jdbc;

    public UserServiceImpl(
            UserRepository users,
            RoleRepository roles,
            PasswordEncoder encoder,
            UserMapper mapper,
            CurrentUserProvider currentUser,
            ActivityLogService audit,
            JdbcTemplate jdbc) {
        this.users = users;
        this.roles = roles;
        this.encoder = encoder;
        this.mapper = mapper;
        this.currentUser = currentUser;
        this.audit = audit;
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('admin')")
    public CurrentUser process(CreateUser command) {
        var created = create(command);
        audit.record(currentUser.getCurrentUser(), "CREATE", "user", created.id(), "User created");
        return created;
    }

    /** Local-only runner, no HTTP route. Serialized and refuses any existing user. */
    @Transactional
    public CurrentUser bootstrap(CreateUser command) {
        jdbc.execute("SELECT pg_advisory_xact_lock(76201902)");
        if (jdbc.queryForObject("SELECT count(*) FROM nourish.app_user", Long.class) != 0) {
            throw new BusinessException(
                    "Initial setup refused: a user already exists. Use authenticated admin APIs.");
        }
        if (!"admin".equals(command.role()))
            throw new BusinessException("Bootstrap requires admin role.");
        var created = create(command);
        audit.record(
                created,
                "BOOTSTRAP",
                "user",
                created.id(),
                "Initial administrator created locally");
        return created;
    }

    private CurrentUser create(CreateUser command) {
        String name = command.username().trim().toLowerCase(Locale.ROOT);
        if (!name.matches("[a-z0-9._-]{3,64}")
                || command.password().length() < 12
                || command.password().getBytes(StandardCharsets.UTF_8).length > 72
                || command.displayName().isBlank()
                || command.displayName().length() > 120) {
            throw new BusinessException(
                    "Use a valid username, display name and password of 12+ characters, at most 72"
                        + " UTF-8 bytes.");
        }
        if (users.existsByUsername(name)) throw new BusinessException("Username already exists.");
        var role =
                roles.findById(command.role())
                        .orElseThrow(() -> new BusinessException("Invalid role."));
        var user = new UserEntity();
        user.setId(UUID.randomUUID().toString());
        user.setUsername(name);
        user.setDisplayName(command.displayName().trim());
        user.setPasswordHash(encoder.encode(command.password()));
        user.setActive(true);
        user.setCreatedAt(Instant.now());
        user.setRoles(new HashSet<>(Set.of(role)));
        users.save(user);
        return mapper.toCurrentUser(user);
    }
}
