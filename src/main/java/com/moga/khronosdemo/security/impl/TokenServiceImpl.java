package com.moga.khronosdemo.security.impl;

import com.moga.khronosdemo.dtos.response.TokenResponseDTO;
import com.moga.khronosdemo.security.JwtProperties;
import com.moga.khronosdemo.security.SecurityUser;
import com.moga.khronosdemo.security.TokenService;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class TokenServiceImpl implements TokenService {

    private final JwtEncoder jwtEncoder;
    private final JwtProperties properties;

    public TokenServiceImpl(JwtEncoder jwtEncoder, JwtProperties properties) {
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
    }

    // The subject is the user id, not the username, so renaming a user does not break their token
    @Override
    public TokenResponseDTO issue(SecurityUser user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.ttl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(String.valueOf(user.getId()))
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim("role", user.getRole().name())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new TokenResponseDTO(token, "Bearer", expiresAt, properties.ttl().toSeconds(), null);
    }
}
