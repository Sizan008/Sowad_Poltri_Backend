package com.sowad.nourish.authentication;

import com.sowad.nourish.authentication.repository.SessionRepository;
import com.sowad.nourish.user.UserMapper;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Service
public class SessionAuthenticationService {
    private final SessionRepository sessions;
    private final TokenCodec tokens;
    private final UserMapper mapper;

    public SessionAuthenticationService(
            SessionRepository sessions, TokenCodec tokens, UserMapper mapper) {
        this.sessions = sessions;
        this.tokens = tokens;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public Optional<SessionPrincipal> authenticate(String token) {
        if (!token.matches("[A-Za-z0-9_-]{43}")) return Optional.empty();
        return sessions.findById(tokens.hash(token))
                .filter(
                        session ->
                                session.getRevokedAt() == null
                                        && session.getExpiresAt().isAfter(Instant.now())
                                        && session.getUser().getActive()
                                        && !session.getUser().getRoles().isEmpty())
                .map(
                        session ->
                                new SessionPrincipal(
                                        mapper.toCurrentUser(session.getUser()),
                                        session.getTokenHash()));
    }
}
