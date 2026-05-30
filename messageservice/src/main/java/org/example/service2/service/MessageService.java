package org.example.service2.service;

import org.example.service2.config.RabbitConfig;
import org.example.service2.dto.MessageRequest;
import org.example.service2.dto.MessageResponse;
import org.example.service2.model.Message;
import org.example.service2.repository.MessageRepository;
import org.example.service3.grpc.GetUserProfileRequest;
import org.example.service3.grpc.UserProfileResponse;
import org.example.service3.grpc.UserServiceGrpc;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class MessageService {

    private final MessageRepository repo;
    private final RabbitTemplate rabbitTemplate;
    private final UserServiceGrpc.UserServiceBlockingStub userServiceStub;

    public MessageService(MessageRepository repo,
                          RabbitTemplate rabbitTemplate,
                          UserServiceGrpc.UserServiceBlockingStub userServiceStub) {
        this.repo = repo;
        this.rabbitTemplate = rabbitTemplate;
        this.userServiceStub = userServiceStub;
    }

    public MessageResponse send(MessageRequest req) {
        Message msg = new Message();
        msg.setContent(req.content());
        msg.setSenderId(req.senderId());
//hela den här betyder:Om UserService är nere eller svarar fel kraschar inte MessageService — meddelandet sparas ändå i databasen,
//bara utan senderUsername. Det är en form av transient felhantering.
        // Fetch sender's username via gRPC call to userservice
        if (req.senderId() != null && !req.senderId().isBlank()) {
            try {
                UserProfileResponse profile = userServiceStub.getUserProfile(
                        GetUserProfileRequest.newBuilder()
                                .setUserId(req.senderId())
                                .build()
                );
                if (profile.getFound()) {
                    msg.setSenderUsername(profile.getUsername());
                }
            } catch (Exception e) {
                // gRPC unavailable — proceed without username
            }
        }

        Message saved = repo.save(msg);

        rabbitTemplate.convertAndSend(
                RabbitConfig.EXCHANGE_NAME,
                RabbitConfig.ROUTING_KEY,
                Map.of(
                        "messageId",     saved.getId(),
                        "content",       saved.getContent(),
                        "senderId",      saved.getSenderId() != null ? saved.getSenderId() : "",
                        "senderUsername", saved.getSenderUsername() != null ? saved.getSenderUsername() : ""
                )
        );

        return toDto(saved);
    }

    public List<MessageResponse> getAll() {
        return repo.findAllByOrderByTimestampDesc().stream().map(this::toDto).toList();
    }

    private MessageResponse toDto(Message m) {
        return new MessageResponse(m.getId(), m.getContent(), m.getSenderId(), m.getSenderUsername(), m.getTimestamp());
    }
}