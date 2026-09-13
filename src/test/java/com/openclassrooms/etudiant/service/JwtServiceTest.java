package com.openclassrooms.etudiant.service;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class JwtServiceTest {
    private static final String SECRET = "test-secret-key-at-least-32-bytes-long!";
    private static final long EXPIRATION_MS = 3_600_000L;

    private JwtService jwtService;

    @BeforeEach
    public void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secret", SECRET);
        ReflectionTestUtils.setField(jwtService, "expirationMs", EXPIRATION_MS);
    }

    private UserDetails userDetails(String username) {
        UserDetails userDetails = mock(UserDetails.class);
        when(userDetails.getUsername()).thenReturn(username);
        return userDetails;
    }

    @Test
    public void generateToken_returnsThreeDotSeparatedSegments() {
        // WHEN
        String token = jwtService.generateToken(userDetails("alice"));

        // THEN
        assertThat(token).isNotNull();
        assertThat(token.split("\\.")).hasSize(3);
    }

    @Test
    public void extractUsername_returnsTheSubjectOfTheToken() {
        // GIVEN
        String token = jwtService.generateToken(userDetails("alice"));

        // WHEN
        String username = jwtService.extractUsername(token);

        // THEN
        assertThat(username).isEqualTo("alice");
    }

    @Test
    public void isTokenValid_returnsTrueForAMatchingUser() {
        // GIVEN
        String token = jwtService.generateToken(userDetails("alice"));

        // WHEN
        boolean valid = jwtService.isTokenValid(token, userDetails("alice"));

        // THEN
        assertThat(valid).isTrue();
    }

    @Test
    public void isTokenValid_returnsFalseWhenUsernameDoesNotMatch() {
        // GIVEN
        String token = jwtService.generateToken(userDetails("alice"));

        // WHEN
        boolean valid = jwtService.isTokenValid(token, userDetails("bob"));

        // THEN
        assertThat(valid).isFalse();
    }

    @Test
    public void isTokenValid_throwsWhenTokenIsExpired() {
        // GIVEN: expirationMs forced negative so the token is already expired the instant it's issued
        ReflectionTestUtils.setField(jwtService, "expirationMs", -1000L);
        String token = jwtService.generateToken(userDetails("alice"));

        // WHEN / THEN
        // jjwt rejects expiration while parsing, before isTokenValid's own username check ever runs
        assertThatThrownBy(() -> jwtService.isTokenValid(token, userDetails("alice")))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    public void isTokenValid_throwsWhenTokenIsSignedWithADifferentKey() {
        // GIVEN: a token signed by a JwtService configured with a different secret
        JwtService otherJwtService = new JwtService();
        ReflectionTestUtils.setField(otherJwtService, "secret", "another-completely-different-32-byte-secret!!");
        ReflectionTestUtils.setField(otherJwtService, "expirationMs", EXPIRATION_MS);
        String token = otherJwtService.generateToken(userDetails("alice"));

        // WHEN / THEN
        assertThatThrownBy(() -> jwtService.isTokenValid(token, userDetails("alice")))
                .isInstanceOf(SignatureException.class);
    }
}
