package org.example.service2.dto;

import java.time.LocalDateTime;

public record MessageResponse(String id, String content, String senderId, LocalDateTime timestamp) {}