package com.centroartesmusicales.backend.security;

import com.centroartesmusicales.backend.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class JwtService {

    private final AppProperties appProperties;

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(appProperties.jwt().secret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(SecurityUser securityUser) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + appProperties.jwt().expirationMs());
        return Jwts.builder()
                .subject(securityUser.getUsername())
                .claim("role", securityUser.getRole().name())
                .claim("nombre", securityUser.getNombre())
                .claim("uid", securityUser.getId())
                .issuedAt(now)
                .expiration(expiration)
                .signWith(signingKey())
                .compact();
    }

    public String extractEmail(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public boolean isTokenValid(String token, SecurityUser securityUser) {
        try {
            String email = extractEmail(token);
            return email.equals(securityUser.getUsername()) && !isExpired(token);
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }

    private boolean isExpired(String token) {
        return extractClaim(token, Claims::getExpiration).before(new Date());
    }

    private <T> T extractClaim(String token, Function<Claims, T> resolver) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return resolver.apply(claims);
    }
}
