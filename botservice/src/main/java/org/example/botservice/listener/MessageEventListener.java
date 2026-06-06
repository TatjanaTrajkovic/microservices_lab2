package org.example.botservice.listener;

import org.example.botservice.config.RabbitConfig;
import org.example.botservice.service.BotService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class MessageEventListener {

    private final BotService botService;

    public MessageEventListener(BotService botService) {
        this.botService = botService;
    }

    @RabbitListener(queues = RabbitConfig.BOT_QUEUE)
    public void onMessagePublished(Map<String, Object> event) {
        botService.handleEvent(event);
    }
}