package org.example.botservice.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    public static final String EXCHANGE_NAME = "chat.exchange";
    public static final String BOT_QUEUE     = "bot.queue";
    public static final String ROUTING_KEY   = "message-published";

    @Bean
    public TopicExchange chatExchange() {
        return new TopicExchange(EXCHANGE_NAME);
    }

    @Bean
    public Queue botQueue() {
        return new Queue(BOT_QUEUE, true);
    }

    @Bean
    public Binding botBinding(Queue botQueue, TopicExchange chatExchange) {
        return BindingBuilder.bind(botQueue).to(chatExchange).with(ROUTING_KEY);
    }

    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}