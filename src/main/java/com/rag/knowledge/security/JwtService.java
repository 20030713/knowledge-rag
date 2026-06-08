package com.rag.knowledge.security;

import com.rag.knowledge.config.AuthProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

@Component
public class JwtService {

    private static final String USER_ID = "userId";
    private static final String USERNAME = "username";
    private static final String ROLE = "role";

    private final AuthProperties authProperties;

    public JwtService(AuthProperties authProperties) {
        this.authProperties = authProperties;
    }

    public String createToken(LoginUser loginUser) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(authProperties.getExpireHours(), ChronoUnit.HOURS);
        return Jwts.builder()
                .subject(String.valueOf(loginUser.userId()))
                .claim(USER_ID, loginUser.userId())
                .claim(USERNAME, loginUser.username())
                .claim(ROLE, loginUser.role())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(secretKey())
                .compact();
    }

    public LoginUser parseToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(secretKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        Long userId = claims.get(USER_ID, Long.class);
        String username = claims.get(USERNAME, String.class);
        String role = claims.get(ROLE, String.class);
        return new LoginUser(userId, username, role);
    }

    private SecretKey secretKey() {
        return Keys.hmacShaKeyFor(authProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }
}
