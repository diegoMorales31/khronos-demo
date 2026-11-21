package com.moga.khronosdemo.security.impl;

import com.moga.khronosdemo.entities.RefreshSession;
import com.moga.khronosdemo.repositories.RefreshSessionRepository;
import com.moga.khronosdemo.repositories.UserRepository;
import com.moga.khronosdemo.security.RefreshTokenProperties;
import com.moga.khronosdemo.security.RefreshTokenService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * Refresh tokens: 32 random bytes, of which only the SHA-256 is kept. Every use replaces the token
 * (rotation) and pushes the session's end idle-ttl further away, so a device in regular use stays
 * signed in. A replaced token that comes back after reuse-grace means two holders of the same token,
 * one of them a copy, so the session ends for both: the owner signs in again, the copy is useless.
 * Inside reuse-grace it is taken as a retry after a lost answer or a second tab racing the first,
 * and only refused, so the client can pick up the token that replaced it.
 */
@Service
@Transactional(noRollbackFor = CredentialsExpiredException.class)
@EnableConfigurationProperties(RefreshTokenProperties.class)
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshSessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final RefreshTokenProperties properties;
    private final Clock clock;

    public RefreshTokenServiceImpl(RefreshSessionRepository sessionRepository, UserRepository userRepository,
                                   RefreshTokenProperties properties, Clock clock) {
        this.sessionRepository = sessionRepository;
        this.userRepository = userRepository;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public String start(Long userId) {
        Instant now = clock.instant();
        sessionRepository.deleteExpiredOf(userId, now);
        String token = newToken();
        sessionRepository.save(new RefreshSession(userRepository.getReferenceById(userId), hash(token), now,
                now.plus(properties.idleTtl())));

        // The sessions used last end latest, so the oldest by expiry go first
        List<RefreshSession> sessions = sessionRepository.findByUser_IdOrderByExpiresAtDesc(userId);
        if (sessions.size() > properties.maxPerUser()) {
            sessionRepository.deleteAll(sessions.subList(properties.maxPerUser(), sessions.size()));
        }
        return token;
    }

    @Override
    public Rotation rotate(String refreshToken) {
        Instant now = clock.instant();
        String hash = hash(refreshToken);
        Optional<RefreshSession> current = sessionRepository.findByTokenHashForUpdate(hash);
        if (current.isEmpty()) {
            sessionRepository.findByPreviousHash(hash).ifPresent(session -> {
                boolean retry = session.getRotatedAt() != null && now.isBefore(session.getRotatedAt().plus(properties.reuseGrace()));
                if (!retry) {
                    sessionRepository.delete(session);
                }
            });
            throw expired();
        }

        RefreshSession session = current.get();
        Instant changedAt = session.getUser().getCredentialsChangedAt();
        if (!now.isBefore(session.getExpiresAt()) || (changedAt != null && session.getCreatedAt().isBefore(changedAt))) {
            sessionRepository.delete(session);
            throw expired();
        }
        String token = newToken();
        session.rotate(hash(token), now, now.plus(properties.idleTtl()));
        return new Rotation(session.getUser().getId(), token);
    }

    @Override
    public void end(String refreshToken) {
        sessionRepository.deleteByTokenHash(hash(refreshToken));
    }

    @Override
    public void endAll(Long userId) {
        sessionRepository.deleteByUserId(userId);
    }

    private static CredentialsExpiredException expired() {
        return new CredentialsExpiredException("The refresh token is not valid");
    }

    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
