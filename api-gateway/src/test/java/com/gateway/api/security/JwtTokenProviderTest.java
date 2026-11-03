
package com.gateway.api.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtTokenProviderTest {

    private static final byte[] SECRET_BYTES =
            "test-secret-key-for-hs256-32-bytes".getBytes();
    private static final String SECRET =
            Base64.getEncoder().encodeToString(SECRET_BYTES);
    private static final String ISSUER = "test-issuer";
    private static final UUID USER_ID =
            UUID.fromString("123e4567-e89b-12d3-a456-426614174000");

    private JwtTokenProvider jwtTokenProvider;
    private SecretKey secretKey;

    @BeforeEach
    void setUp() {
        secretKey = Keys.hmacShaKeyFor(SECRET_BYTES);

        jwtTokenProvider = new JwtTokenProvider(
                SECRET,
                Duration.ofMinutes(15),
                ISSUER
        );
    }

    // Test 1: Verify Engineer 1 generates all required JWT claims.
    @Test
    void generateToken_shouldContainAllRequiredClaims() {
        String token = jwtTokenProvider.generateToken(
                USER_ID,
                List.of("ROLE_USER", "ROLE_ADMIN"),
                "PRO"
        );

        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .requireIssuer(ISSUER)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        assertEquals(USER_ID.toString(), claims.getSubject());
        assertEquals(ISSUER, claims.getIssuer());
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
                USER_ID,
                List.of("ROLE_USER"),
                "FREE"
        );

        Claims claims = jwtTokenProvider.validateToken(token);

        assertEquals(USER_ID, jwtTokenProvider.getUserId(token));
        assertEquals(List.of("ROLE_USER"), jwtTokenProvider.getRoles(token));
        assertEquals("FREE", jwtTokenProvider.getTier(token));
        assertEquals(USER_ID.toString(), claims.getSubject());
    }

    // Test 3: Verify expired tokens are rejected.
    @Test
    void validateToken_shouldRejectExpiredToken() {
        long now = System.currentTimeMillis();

        String expiredToken = Jwts.builder()
                .subject(USER_ID.toString())
                .issuer(ISSUER)
                .claim("roles", List.of("ROLE_USER"))
                .claim("tier", "FREE")
                .issuedAt(new Date(now - 60_000))
                .expiration(new Date(now - 1_000))
                .signWith(secretKey)
                .compact();

        assertThrows(
                io.jsonwebtoken.ExpiredJwtException.class,
                () -> jwtTokenProvider.validateToken(expiredToken),
                "An expired JWT must be rejected"
        );
    }

    // Test 4: Verify tokens with modified signatures are rejected.
    @Test
    void validateToken_shouldRejectTamperedSignature() {
        String validToken = jwtTokenProvider.generateToken(
                USER_ID,
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

        assertThrows(
                io.jsonwebtoken.JwtException.class,
                () -> jwtTokenProvider.validateToken(tamperedToken),
                "A JWT with a tampered signature must be rejected"
        );
    }
}
