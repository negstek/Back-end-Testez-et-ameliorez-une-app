package com.openclassrooms.etudiant.service;


import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class JwtService {

    // HMAC signing key, must be kept server-side only (never exposed to clients)
    private final String secret;

    // Token time-to-live in milliseconds
    private final long expirationMs;

    // Constructor injection (rather than field injection) lets tests build a JwtService
    // directly with fixed values, with no need for reflection to reach the private fields.
    public JwtService(@Value("${jwt.secret}") String secret,
                       @Value("${jwt.expiration-ms}") long expirationMs) {
        this.secret = secret;
        this.expirationMs = expirationMs;
    }

    public String generateToken(UserDetails userDetails) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + expirationMs);

        // Builds a signed JWT (header.payload.signature): subject identifies the user,
        // issuedAt/expiration bound its validity window, signWith seals it against tampering.
        return Jwts.builder()
                .subject(userDetails.getUsername())
                .issuedAt(now)
                .expiration(expiration)
                .signWith(getSigningKey())
                .compact();
    }

    public String extractUsername(String token) {
        return extractClaims(token).getSubject();
    }

    // Signature and expiration are both checked here: parseSignedClaims throws on an
    // invalid signature, and getExpiration()/before(now) rejects an expired token.
    public boolean isTokenValid(String token, UserDetails userDetails) {
        String username = extractUsername(token);
        return username.equals(userDetails.getUsername()) && !isTokenExpired(token);
    }

    private boolean isTokenExpired(String token) {
        return extractClaims(token).getExpiration().before(new Date());
    }

    // Parses the token and returns its payload (claims: subject, issuedAt, expiration...).
    // verifyWith() makes parseSignedClaims() check the signature against this service's key,
    // throwing (SignatureException/ExpiredJwtException/...) rather than returning claims if the
    // token was tampered with or has expired — this is the single choke point both
    // extractUsername() and isTokenExpired() go through to read a token's payload.
    private Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    // Derives an HMAC-SHA key from the raw secret for signing/verifying tokens
    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

}
