package com.openclassrooms.etudiant.service;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// "unit": no Spring context, no Docker — safe and fast to run in the pre-commit hook
@Tag("unit")
public class JwtServiceTest {
    private static final String SECRET = "test-secret-key-at-least-32-bytes-long!";
    private static final long EXPIRATION_MS = 3_600_000L;

    private JwtService jwtService;

    @BeforeEach
    public void setUp() {
        jwtService = new JwtService(SECRET, EXPIRATION_MS);
    }

    private UserDetails userDetails(String username) {
        UserDetails userDetails = mock(UserDetails.class);
        when(userDetails.getUsername()).thenReturn(username);
        return userDetails;
    }

    // Verifies that a JWT has the standard header.payload.signature shape: 3 dot-separated segments
    @Test
    public void generateToken_returnsThreeDotSeparatedSegments() {
        // WHEN
        String token = jwtService.generateToken(userDetails("alice"));

        // THEN
        assertThat(token).isNotNull();
        assertThat(token.split("\\.")).hasSize(3);
    }

    // Verifies that the subject claim set at generation time round-trips back out unchanged
    @Test
    public void extractUsername_returnsTheSubjectOfTheToken() {
        // GIVEN
        String token = jwtService.generateToken(userDetails("alice"));

        // WHEN
        String username = jwtService.extractUsername(token);

        // THEN
        assertThat(username).isEqualTo("alice");
    }

    // Verifies that a freshly generated, unexpired token validates for the user it was issued to
    @Test
    public void isTokenValid_returnsTrueForAMatchingUser() {
        // GIVEN
        String token = jwtService.generateToken(userDetails("alice"));

        // WHEN
        boolean valid = jwtService.isTokenValid(token, userDetails("alice"));

        // THEN
        assertThat(valid).isTrue();
    }

    // Verifies that a token issued for one user does not validate against a different user's UserDetails
    @Test
    public void isTokenValid_returnsFalseWhenUsernameDoesNotMatch() {
        // GIVEN
        String token = jwtService.generateToken(userDetails("alice"));

        // WHEN
        boolean valid = jwtService.isTokenValid(token, userDetails("bob"));

        // THEN
        assertThat(valid).isFalse();
    }

    // Verifies that an expired token is rejected, even though jjwt does so via an exception rather than a boolean
    @Test
    public void isTokenValid_throwsWhenTokenIsExpired() {
        // GIVEN: a dedicated instance with a negative expirationMs, so the token is already expired the instant it's issued
        JwtService expiredJwtService = new JwtService(SECRET, -1000L);
        String token = expiredJwtService.generateToken(userDetails("alice"));

        // WHEN / THEN
        // jjwt rejects expiration while parsing, before isTokenValid's own username check ever runs;
        // validating with jwtService (same secret, unrelated expirationMs) confirms only the token's own claims matter here
        assertThatThrownBy(() -> jwtService.isTokenValid(token, userDetails("alice")))
                .isInstanceOf(ExpiredJwtException.class);
    }

    // Verifies that a token whose signature doesn't match this service's key is rejected as tampered/forged
    @Test
    public void isTokenValid_throwsWhenTokenIsSignedWithADifferentKey() {
        // GIVEN: a token signed by a JwtService configured with a different secret
        JwtService otherJwtService = new JwtService("another-completely-different-32-byte-secret!!", EXPIRATION_MS);
        String token = otherJwtService.generateToken(userDetails("alice"));

        // WHEN / THEN
        assertThatThrownBy(() -> jwtService.isTokenValid(token, userDetails("alice")))
                .isInstanceOf(SignatureException.class);
    }
}
