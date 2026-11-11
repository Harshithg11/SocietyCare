package com.societycare.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.User;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-key-with-more-than-32-chars";

    private UserDetails userDetails(String email) {
        return User.withUsername(email).password("ignored").authorities("ROLE_CITIZEN").build();
    }

    @Test
    void generatedTokenIsValidAndContainsEmail() {
        JwtService jwtService = new JwtService(SECRET, 3_600_000L);
        String token = jwtService.generateToken("a@b.com", "CITIZEN");

        assertThat(jwtService.extractUsername(token)).isEqualTo("a@b.com");
        assertThat(jwtService.isTokenValid(token, userDetails("a@b.com"))).isTrue();
        assertThat(jwtService.isTokenValid(token, userDetails("other@b.com"))).isFalse();
    }

    @Test
    void expiredTokenIsRejected() {
        JwtService jwtService = new JwtService(SECRET, -1_000L);
        String token = jwtService.generateToken("a@b.com", "CITIZEN");

        assertThat(jwtService.isTokenValid(token, userDetails("a@b.com"))).isFalse();
    }

    @Test
    void tamperedTokenIsRejected() {
        JwtService jwtService = new JwtService(SECRET, 3_600_000L);
        String token = jwtService.generateToken("a@b.com", "CITIZEN");
        String tampered = token.substring(0, token.length() - 2) + (token.endsWith("AA") ? "BB" : "AA");

        assertThat(jwtService.isTokenValid(tampered, userDetails("a@b.com"))).isFalse();
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        JwtService issuer = new JwtService("another-secret-key-with-more-than-32-characters", 3_600_000L);
        JwtService verifier = new JwtService(SECRET, 3_600_000L);

        String token = issuer.generateToken("a@b.com", "CITIZEN");
        assertThat(verifier.isTokenValid(token, userDetails("a@b.com"))).isFalse();
    }

    @Test
    void shortSecretIsRejectedAtStartup() {
        assertThatThrownBy(() -> new JwtService("too-short", 1000L))
                .isInstanceOf(IllegalStateException.class);
    }
}
