package com.gateway.api.security;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

@Component
public class JwtTokenProvider {
    
    private final SecretKey signingKey;
    private final Duration accessTokenTtl;
    private final String issuer; 

     public JwtTokenProvider(
            @Value("${security.jwt.secret}") String secret,
            @Value("${security.jwt.access-token-ttl:15m}") Duration accessTokenTtl,
            @Value("${security.jwt.issuer:llm-api-gateway}") String issuer) {

        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.accessTokenTtl = accessTokenTtl;
        this.issuer = issuer;
    }

    public String generateToken(
            UUID userId,
            List<String> roles,
            String tier) {

        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(accessTokenTtl);

        return Jwts.builder()
                .subject(userId.toString())
                .issuer(issuer)
                .claim("roles", roles)
                .claim("tier", tier)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey)
                .compact();
    }

    public Claims validateToken(String token) {
        Jws<Claims> parsedToken = Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token);

        return parsedToken.getPayload();
    }

    public UUID getUserId(String token) {
        return UUID.fromString(validateToken(token).getSubject());
    }

    public List<String> getRoles(String token) {
        return validateToken(token).get("roles", List.class);
    }

    public String getTier(String token) {
        return validateToken(token).get("tier", String.class);
    }
}
