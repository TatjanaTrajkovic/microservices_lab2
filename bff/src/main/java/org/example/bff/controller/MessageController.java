package org.example.bff.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/messages")
public class MessageController {

    @Value("${services.message-url}")
    private String messageUrl;

    @PostMapping
    public ResponseEntity<Object> send(@RequestBody Map<String, String> body, Principal principal) {
        Map<String, String> payload = new HashMap<>();
        payload.put("content",  body.get("content"));
        payload.put("senderId", principal.getName()); // userId från JWT

        Object response = RestClient.create(messageUrl)
                .post()
                .uri("/api/messages")
                .body(payload)
                .retrieve()
                .body(Object.class);
        return ResponseEntity.status(201).body(response);
    }

    @GetMapping
    public ResponseEntity<Object> getAll() {
        Object messages = RestClient.create(messageUrl)
                .get()
                .uri("/api/messages")
                .retrieve()
                .body(Object.class);
        return ResponseEntity.ok(messages);
    }
}