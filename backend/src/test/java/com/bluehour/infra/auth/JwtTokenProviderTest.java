package com.bluehour.infra.auth;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderTest {

    private static final String SECRET = "test-secret-key-that-is-at-least-32-bytes-long!!";
    private static final int EXPIRY_HOURS = 24;

    private JwtTokenProvider sut;

    @BeforeEach
    void setUp() {
        sut = new JwtTokenProvider(SECRET, EXPIRY_HOURS);
    }

    @Test
    @DisplayName("generateToken → 유효한 JWT 문자열 반환")
    void generateToken_성공() {
        String token = sut.generateToken(1L);

        assertThat(token).isNotNull().isNotBlank();
        assertThat(token.split("\\.")).hasSize(3);
    }

    @Test
    @DisplayName("getMemberId → 정확한 memberId 추출")
    void getMemberId_성공() {
        String token = sut.generateToken(42L);

        Long memberId = sut.getMemberId(token);

        assertThat(memberId).isEqualTo(42L);
    }

    @Test
    @DisplayName("isValid → 유효한 토큰 true")
    void isValid_유효한_토큰() {
        String token = sut.generateToken(1L);

        assertThat(sut.isValid(token)).isTrue();
    }

    @Test
    @DisplayName("isValid → 만료된 토큰 false")
    void isValid_만료된_토큰() {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        String expiredToken = Jwts.builder()
                .subject("1")
                .issuedAt(new Date(System.currentTimeMillis() - 100_000))
                .expiration(new Date(System.currentTimeMillis() - 50_000))
                .signWith(key)
                .compact();

        assertThat(sut.isValid(expiredToken)).isFalse();
    }

    @Test
    @DisplayName("isValid → 변조된 토큰 false")
    void isValid_변조된_토큰() {
        String token = sut.generateToken(1L);
        String tampered = token.substring(0, token.length() - 5) + "XXXXX";

        assertThat(sut.isValid(tampered)).isFalse();
    }

    @Test
    @DisplayName("isValid → null/blank 토큰 false")
    void isValid_null_blank() {
        assertThat(sut.isValid(null)).isFalse();
        assertThat(sut.isValid("")).isFalse();
        assertThat(sut.isValid("   ")).isFalse();
    }

    @Test
    @DisplayName("생성자 → 빈 secret 거부")
    void constructor_빈_secret_거부() {
        assertThatThrownBy(() -> new JwtTokenProvider(" ", EXPIRY_HOURS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("JWT secret은 필수입니다.");
    }

    @Test
    @DisplayName("생성자 → 32바이트 미만 secret 거부")
    void constructor_짧은_secret_거부() {
        assertThatThrownBy(() -> new JwtTokenProvider("too-short", EXPIRY_HOURS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("JWT secret은 최소 32바이트여야 합니다.");
    }
}
