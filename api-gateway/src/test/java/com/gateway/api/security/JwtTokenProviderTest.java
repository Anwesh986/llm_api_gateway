
package com.gateway.api.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenProviderTest {

    private static final String SECRET =
            "test-secret-key-for-hs256-must-be-32-bytes";

    private JwtTokenProvider jwtTokenProvider;
    private SecretKey secretKey;

    @BeforeEach
    void setUp() {
        secretKey = Keys.hmacShaKeyFor(
                SECRET.getBytes(StandardCharsets.UTF_8)
        );

        jwtTokenProvider = new JwtTokenProvider(SECRET);
    }

    // Test 1: Verify Engineer 1 generates all required JWT claims.
    @Test
    void generateToken_shouldContainAllRequiredClaims() {
        String token = jwtTokenProvider.generateToken(
                "user-123",
                List.of("ROLE_USER", "ROLE_ADMIN"),
                "PRO"
        );

        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        assertEquals("user-123", claims.getSubject());
        assertEquals(
                List.of("ROLE_USER", "ROLE_ADMIN"),
                claims.get("roles", List.class)
        );
        assertEquals("PRO", claims.get("tier"));

        assertNotNull(claims.getIssuedAt(), "iat must exist");
        assertNotNull(claims.getExpiration(), "exp must exist");

        assertTrue(
                claims.getExpiration().after(claims.getIssuedAt()),
                "exp must be later than iat"
        );
    }

    // Test 2: Verify a correctly signed, unexpired token is accepted.
    @Test
    void validateToken_shouldAcceptValidToken() {
        String token = jwtTokenProvider.generateToken(
                "user-123",
                List.of("ROLE_USER"),
                "FREE"
        );

        assertTrue(
                jwtTokenProvider.validateToken(token),
                "A valid JWT must be accepted"
        );
    }

    // Test 3: Verify expired tokens are rejected.
    @Test
    void validateToken_shouldRejectExpiredToken() {
        long now = System.currentTimeMillis();

        String expiredToken = Jwts.builder()
                .subject("user-123")
                .claim("roles", List.of("ROLE_USER"))
                .claim("tier", "FREE")
                .issuedAt(new Date(now - 60_000))
                .expiration(new Date(now - 1_000))
                .signWith(secretKey)
                .compact();

        assertFalse(
                jwtTokenProvider.validateToken(expiredToken),
                "An expired JWT must be rejected"
        );
    }

    // Test 4: Verify tokens with modified signatures are rejected.
    @Test
    void validateToken_shouldRejectTamperedSignature() {
        String validToken = jwtTokenProvider.generateToken(
                "user-123",
                List.of("ROLE_USER"),
                "FREE"
        );

        String[] parts = validToken.split("\\.");

        assertEquals(3, parts.length);

        // Modify the signature without re-signing the token.
        char firstChar = parts[2].charAt(0);
        parts[2] = (firstChar == 'a' ? "b" : "a")
                + parts[2].substring(1);

        String tamperedToken = String.join(".", parts);

        assertFalse(
                jwtTokenProvider.validateToken(tamperedToken),
                "A JWT with a tampered signature must be rejected"
        );
    }
}
