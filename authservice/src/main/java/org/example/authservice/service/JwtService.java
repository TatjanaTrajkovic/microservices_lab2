package org.example.authservice.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String secret;

    //här är secretkey som kan inte manipuleras utan nyckel
    private SecretKey getKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    //token gäller i 24 timmar
    public String generateToken(String userId, String username) {
        return Jwts.builder()
                .subject(userId)
                .claim("username", username)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 86_400_000L))
                .signWith(getKey())// signeras med HS256
                .compact();
    }

    public Map<String, String> verifyToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getKey()) //samma hemliga nyckel
                .build()
                .parseSignedClaims(token)   //kastar undantag om signatur är fel
                .getPayload();
        return Map.of(
                "userId",   claims.getSubject(),
                "username", claims.get("username", String.class)
        );
    }
}