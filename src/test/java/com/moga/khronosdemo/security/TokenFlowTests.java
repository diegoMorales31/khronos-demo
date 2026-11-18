package com.moga.khronosdemo.security;

import com.moga.khronosdemo.dtos.response.TokenResponseDTO;
import com.moga.khronosdemo.entities.enums.Role;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class TokenFlowTests {

    private static final String SECRET = "test-secret-that-is-at-least-32-bytes-long";
    private final JwtConfig config = new JwtConfig();

    private SecurityUser user(boolean enabled, Instant credentialsChangedAt) {
        return new SecurityUser(7L, "lucia", "{noop}x", Role.WORKER, enabled, credentialsChangedAt);
    }

    private TokenService tokenService(String secret, Duration ttl) {
        SecretKey key = config.jwtSigningKey(new JwtProperties(secret, ttl));
        return new TokenService(config.jwtEncoder(key), new JwtProperties(secret, ttl));
    }

    private JwtDecoder decoder() {
        return config.jwtDecoder(config.jwtSigningKey(new JwtProperties(SECRET, Duration.ofHours(1))));
    }

    private JwtUserAuthenticationConverter converterReturning(SecurityUser user) {
        return new JwtUserAuthenticationConverter(new DatabaseUserDetailsService(null) {
            @Override
            public Optional<SecurityUser> loadUserById(Long id) {
                return id.equals(user.getId()) ? Optional.of(user) : Optional.empty();
            }
        });
    }

    @Test
    void validTokenAuthenticatesAsTheSameUserForOneHour() {
        TokenResponseDTO token = tokenService(SECRET, Duration.ofHours(1)).issue(user(true, null));

        assertEquals(3600, token.expiresIn());
        assertEquals("Bearer", token.tokenType());
        AbstractAuthenticationToken auth = converterReturning(user(true, null)).convert(decoder().decode(token.accessToken()));
        assertInstanceOf(SecurityUser.class, auth.getPrincipal());
        assertEquals(7L, ((SecurityUser) auth.getPrincipal()).getId());
        assertTrue(auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_WORKER")));
    }

    @Test
    void expiredTokenIsRejected() {
        // Issued two hours ago with the one hour lifetime, so well past the decoder's 60 second skew
        SecretKey key = config.jwtSigningKey(new JwtProperties(SECRET, Duration.ofHours(1)));
        Instant issuedAt = Instant.now().minus(Duration.ofHours(2));
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(TokenService.ISSUER)
                .subject("7")
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(Duration.ofHours(1)))
                .build();
        String token = config.jwtEncoder(key)
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();

        assertThrows(JwtValidationException.class, () -> decoder().decode(token));
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() {
        TokenResponseDTO token = tokenService("another-secret-that-is-also-32-bytes-long!", Duration.ofHours(1))
                .issue(user(true, null));

        assertThrows(BadJwtException.class, () -> decoder().decode(token.accessToken()));
    }

    @Test
    void passwordChangeRevokesEarlierTokens() {
        TokenResponseDTO token = tokenService(SECRET, Duration.ofHours(1)).issue(user(true, null));
        SecurityUser changedLater = user(true, Instant.now().plusSeconds(5));

        assertThrows(CredentialsExpiredException.class,
                () -> converterReturning(changedLater).convert(decoder().decode(token.accessToken())));
    }

    @Test
    void tokenIssuedRightAfterPasswordChangeIsAccepted() {
        SecurityUser changedNow = user(true, Instant.now());
        TokenResponseDTO token = tokenService(SECRET, Duration.ofHours(1)).issue(changedNow);

        assertNotNull(converterReturning(changedNow).convert(decoder().decode(token.accessToken())));
    }

    @Test
    void deactivatedUserIsRejectedEvenWithAValidToken() {
        TokenResponseDTO token = tokenService(SECRET, Duration.ofHours(1)).issue(user(true, null));

        assertThrows(DisabledException.class,
                () -> converterReturning(user(false, null)).convert(decoder().decode(token.accessToken())));
    }

    @Test
    void shortSecretFailsAtStartup() {
        assertThrows(IllegalStateException.class,
                () -> config.jwtSigningKey(new JwtProperties("too-short", Duration.ofHours(1))));
    }
}
