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
        var builder = Jwts.builder()
                .subject(principal.employeeNo())
                .claim("employeeNo", principal.employeeNo())
                .claim("email", principal.email())
                .claim("name", principal.name())
                .claim("department", principal.department())
                .claim("role", principal.role().name())
                .issuedAt(now)
                .expiration(exp);
        if (principal.userId() != null) {
            builder = builder.claim("uid", principal.userId());
        }
        return builder.signWith(getSigningKey()).compact();
    }

    public Optional<UserPrincipal> parseToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            Long userId = null;
            Object uidObj = claims.get("uid");
            if (uidObj instanceof Number n) {
                userId = n.longValue();
            }

            String claimEmp = claims.get("employeeNo", String.class);
            String subject = claims.getSubject();
            String employeeNo = firstNonBlank(claimEmp, subject);
            if (employeeNo != null) {
                employeeNo = employeeNo.trim();
            }
            if ((employeeNo == null || employeeNo.isBlank()) && userId == null) {
                return Optional.empty();
            }

            String email = claims.get("email", String.class);
            String name = claims.get("name", String.class);
            String department = claims.get("department", String.class);
            AppRole role = AppRole.parse(claims.get("role", String.class));
            if (role == null) {
                role = AppRole.MEMBER;
            }
            return Optional.of(new UserPrincipal(userId, employeeNo, email, name, department, role));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        if (b != null && !b.isBlank()) {
            return b;
        }
        return null;
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < MIN_KEY_BYTES) {
            keyBytes = Arrays.copyOf(keyBytes, MIN_KEY_BYTES);
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
