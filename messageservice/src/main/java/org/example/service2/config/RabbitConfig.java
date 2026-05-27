package org.example.service2.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    public static final String EXCHANGE_NAME = "chat.exchange";
    public static final String QUEUE_NAME    = "message.published";
    public static final String ROUTING_KEY   = "message-published";

    @Bean
    public TopicExchange chatExchange() {
        return new TopicExchange(EXCHANGE_NAME);
    }

    @Bean
    public Queue messagePublishedQueue() {
        return new Queue(QUEUE_NAME, true);
    }

    @Bean
    public Binding binding(Queue messagePublishedQueue, TopicExchange chatExchange) {
        return BindingBuilder.bind(messagePublishedQueue).to(chatExchange).with(ROUTING_KEY);
    }
}