package org.example.service2.service;

import org.example.service2.config.RabbitConfig;
import org.example.service2.dto.MessageRequest;
import org.example.service2.dto.MessageResponse;
import org.example.service2.model.Message;
import org.example.service2.repository.MessageRepository;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class MessageService {

    private final MessageRepository repo;
    private final RabbitTemplate rabbitTemplate;

    public MessageService(MessageRepository repo, RabbitTemplate rabbitTemplate) {
        this.repo = repo;
        this.rabbitTemplate = rabbitTemplate;
    }

    public MessageResponse send(MessageRequest req) {
        Message msg = new Message();
        msg.setContent(req.content());
        msg.setSenderId(req.senderId());
        Message saved = repo.save(msg);

        // Publicera message-published-händelse till Message Queue
        rabbitTemplate.convertAndSend(
                RabbitConfig.EXCHANGE_NAME,
                RabbitConfig.ROUTING_KEY,
                Map.of(
                        "messageId", saved.getId(),
                        "content",   saved.getContent(),
                        "senderId",  saved.getSenderId() != null ? saved.getSenderId() : ""
                )
        );

        return toDto(saved);
    }

    public List<MessageResponse> getAll() {
        return repo.findAllByOrderByTimestampDesc().stream().map(this::toDto).toList();
    }

    private MessageResponse toDto(Message m) {
        return new MessageResponse(m.getId(), m.getContent(), m.getSenderId(), m.getTimestamp());
    }
}
