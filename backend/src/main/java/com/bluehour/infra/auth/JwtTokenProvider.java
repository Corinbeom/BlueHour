package com.bluehour.infra.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtTokenProvider {

    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey key;
    private final long expiryMillis;

    public JwtTokenProvider(
            @Value("${bluehour.jwt.secret}") String secret,
            @Value("${bluehour.jwt.expiry-hours}") int expiryHours
    ) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT secret은 필수입니다.");
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("JWT secret은 최소 32바이트여야 합니다.");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiryMillis = (long) expiryHours * 3600 * 1000;
    }

    public String generateToken(Long memberId) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expiryMillis);
        return Jwts.builder()
                .subject(String.valueOf(memberId))
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    public Long getMemberId(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return Long.parseLong(claims.getSubject());
    }

    public boolean isValid(String token) {
        try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}
