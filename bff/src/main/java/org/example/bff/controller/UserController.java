package org.example.bff.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

@RestController
@RequestMapping("/api/users")
public class UserController {

    @Value("${services.user-url}")
    private String userUrl;

    @GetMapping("/{id}")
    public ResponseEntity<Object> getById(@PathVariable String id) {
        Object user = RestClient.create(userUrl)
                .get()
                .uri("/api/users/" + id)
                .retrieve()
                .body(Object.class);
        return ResponseEntity.ok(user);
    }

    @GetMapping
    public ResponseEntity<Object> getAll() {
        Object users = RestClient.create(userUrl)
                .get()
                .uri("/api/users")
                .retrieve()
                .body(Object.class);
        return ResponseEntity.ok(users);
    }
}