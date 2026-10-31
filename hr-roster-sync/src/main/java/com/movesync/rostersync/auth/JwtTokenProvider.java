package com.movesync.rostersync.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private final long validityMinutes;

    public JwtTokenProvider(
            @Value("${app.jwt.secret:MoveInSyncRosterSyncSuperSecretKeyForJwtAuthenticationMustBeAtLeast256BitsLong}") String secret,
            @Value("${app.jwt.validity-minutes:1440}") long validityMinutes) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.validityMinutes = validityMinutes;
    }

    public String generateToken(String subject, UUID tenantId, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(subject)
                .claim("tenant_id", tenantId.toString())
                .claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(validityMinutes, ChronoUnit.MINUTES)))
                .signWith(key)
                .compact();
    }

    public Claims parseAndValidate(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}

