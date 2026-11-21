package com.sowad.nourish.authentication;

import com.sowad.nourish.activitylog.ActivityLogService;
import com.sowad.nourish.authentication.command.Login;
import com.sowad.nourish.authentication.query.LoginResult;
import com.sowad.nourish.authentication.repository.SessionRepository;
import com.sowad.nourish.authentication.repository.entity.SessionEntity;
import com.sowad.nourish.common.security.SecurityContextCurrentUserProvider;
import com.sowad.nourish.user.UserMapper;
import com.sowad.nourish.user.repository.UserRepository;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

@Service
public class AuthenticationServiceImpl implements AuthenticationService {
    private final UserRepository users;
    private final SessionRepository sessions;
    private final PasswordEncoder encoder;
    private final TokenCodec tokens;
    private final UserMapper mapper;
    private final ActivityLogService audit;
    private final SecurityContextCurrentUserProvider currentUser;
    private final String dummyHash;

    public AuthenticationServiceImpl(
            UserRepository users,
            SessionRepository sessions,
            PasswordEncoder encoder,
            TokenCodec tokens,
            UserMapper mapper,
            ActivityLogService audit,
            SecurityContextCurrentUserProvider currentUser) {
        this.users = users;
        this.sessions = sessions;
        this.encoder = encoder;
        this.tokens = tokens;
        this.mapper = mapper;
        this.audit = audit;
        this.currentUser = currentUser;
        this.dummyHash = encoder.encode(tokens.generate());
    }

    @Override
    @Transactional(noRollbackFor = BadCredentialsException.class)
    public LoginResult process(Login command) {
        var found = users.findForLogin(command.username().trim().toLowerCase(Locale.ROOT));
        var user = found.orElse(null);
        boolean matches =
                command.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 72
                        && encoder.matches(
                                command.password(),
                                user == null ? dummyHash : user.getPasswordHash());
        Instant now = Instant.now();
        boolean locked =
                user != null && user.getLockedUntil() != null && user.getLockedUntil().isAfter(now);
        if (user == null || !matches || !user.getActive() || locked || user.getRoles().isEmpty()) {
            if (user != null && !locked && user.getActive()) {
                int attempts = user.getLockedUntil() != null ? 1 : user.getFailedAttempts() + 1;
                user.setFailedAttempts(attempts);
                user.setLockedUntil(attempts >= 5 ? now.plus(Duration.ofMinutes(15)) : null);
            }
            audit.record(null, "LOGIN_FAILED", "session", null, "Authentication rejected");
            throw new BadCredentialsException(
                    "Invalid credentials or login temporarily unavailable.");
        }
        user.setFailedAttempts(0);
        user.setLockedUntil(null);
        String token = tokens.generate();
        var session = new SessionEntity();
        session.setTokenHash(tokens.hash(token));
        session.setUser(user);
        session.setCreatedAt(now);
        session.setExpiresAt(now.plus(Duration.ofHours(8)));
        sessions.save(session);
        var actor = mapper.toCurrentUser(user);
        audit.record(actor, "LOGIN", "session", null, "User logged in");
        return new LoginResult(token, "Bearer", session.getExpiresAt(), actor);
    }

    @Override
    @Transactional
    public void logout() {
        var principal = currentUser.getSession();
        sessions.findById(principal.tokenHash())
                .ifPresent(session -> session.setRevokedAt(Instant.now()));
        audit.record(principal.user(), "LOGOUT", "session", null, "User logged out");
    }
}
