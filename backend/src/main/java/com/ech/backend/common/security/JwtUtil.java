package com.ech.backend.common.security;

import com.ech.backend.common.rbac.AppRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtUtil {

    private static final int MIN_KEY_BYTES = 32;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration-ms}")
    private long expirationMs;

    public String generateToken(UserPrincipal principal) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + expirationMs);
        return Jwts.builder()
                .subject(principal.employeeNo())
                .claim("employeeNo", principal.employeeNo())
                .claim("email", principal.email())
                .claim("name", principal.name())
                .claim("department", principal.department())
                .claim("role", principal.role().name())
                .issuedAt(now)
                .expiration(exp)
                .signWith(getSigningKey())
                .compact();
    }

    public Optional<UserPrincipal> parseToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String employeeNo = claims.getSubject();
            String email = claims.get("email", String.class);
            String name = claims.get("name", String.class);
            String department = claims.get("department", String.class);
            AppRole role = AppRole.parse(claims.get("role", String.class));
            if (role == null) {
                role = AppRole.MEMBER;
            }
            return Optional.of(new UserPrincipal(employeeNo, email, name, department, role));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < MIN_KEY_BYTES) {
            keyBytes = Arrays.copyOf(keyBytes, MIN_KEY_BYTES);
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
