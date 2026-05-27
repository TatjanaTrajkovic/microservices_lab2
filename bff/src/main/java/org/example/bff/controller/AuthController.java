package org.example.bff.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class AuthController {

    @Value("${services.auth-url}")
    private String authUrl;

    @Value("${services.user-url}")
    private String userUrl;

    @PostMapping("/auth/login")
    public ResponseEntity<Object> login(@RequestBody Map<String, String> body) {
        Object response = RestClient.create(authUrl)
                .post()
                .uri("/api/auth/login")
                .body(body)
                .retrieve()
                .body(Object.class);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/users")
    public ResponseEntity<Object> register(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String email    = body.getOrDefault("email", "");
        String password = body.get("password");

        // 1. Skapa användarprofil i User Service (service3)
        @SuppressWarnings("unchecked")
        Map<String, Object> userResp = RestClient.create(userUrl)
                .post()
                .uri("/api/users")
                .body(Map.of("username", username, "email", email))
                .retrieve()
                .body(Map.class);

        // 2. Registrera inloggningsuppgifter i Auth Service
        RestClient.create(authUrl)
                .post()
                .uri("/api/auth/register")
                .body(Map.of(
                        "userId",   userResp.get("userId"),
                        "username", username,
                        "password", password
                ))
                .retrieve()
                .toBodilessEntity();

        return ResponseEntity.status(201).body(userResp);
    }
}