package org.example.orderservice.service;

import org.example.orderservice.config.RabbitConfig;
import org.example.event.OrderPlacedEvent;
import org.example.orderservice.model.OutboxEvent;
import org.example.orderservice.repository.OutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OutboxRelay {
    private static final Logger logger = LoggerFactory.getLogger(OutboxRelay.class);
    private final OutboxRepository outboxRepository;
    private final RabbitTemplate rabbitTemplate;
    private final org.example.orderservice.controller.ChaosContext chaosContext;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    public OutboxRelay(OutboxRepository outboxRepository, RabbitTemplate rabbitTemplate, org.example.orderservice.controller.ChaosContext chaosContext, com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.chaosContext = chaosContext;
        this.objectMapper = objectMapper;
        
        this.rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
            if (ack && correlationData != null) {
                Long id = Long.valueOf(correlationData.getId());
                updateStatus(id, OutboxEvent.OutboxStatus.PROCESSED);
                logger.info("Message {} successfully published and acked", id);
            } else if (correlationData != null) {
                Long id = Long.valueOf(correlationData.getId());
                logger.error("Message {} failed to publish: {}", id, cause);
                // Optionally retry or mark as FAILED
            }
        });
    }

    private void updateStatus(Long id, OutboxEvent.OutboxStatus status) {
        outboxRepository.findById(id).ifPresent(event -> {
            event.setStatus(status);
            outboxRepository.save(event);
        });
    }

    @Scheduled(fixedDelay = 5000)
    public void relayEvents() {
        List<OutboxEvent> pendingEvents = outboxRepository.findByStatus(OutboxEvent.OutboxStatus.PENDING);
        for (OutboxEvent event : pendingEvents) {
            try {
                String payload = event.getPayload();
                var scenario = chaosContext.getCurrentScenario();
                
                if (scenario == org.example.orderservice.controller.ChaosScenario.DATA_CORRUPTION) {
                    payload = "{\"corrupted\": \"true\", \"quantity\": -99}";
                    logger.warn("Chaos: Corrupting payload for event {}", event.getEventId());
                }

                logger.info("Outbox Recovery: Relaying pending event {} (Aggregate ID: {})", event.getEventId(), event.getAggregateId());
                logger.debug("Relaying event: {} with scenario {}", event.getEventId(), scenario);
                CorrelationData correlationData = new CorrelationData(event.getId().toString());
                
                Object messagePayload = event.getPayload();
                if (scenario != org.example.orderservice.controller.ChaosScenario.DATA_CORRUPTION) {
                    try {
                        messagePayload = objectMapper.readValue(event.getPayload(), OrderPlacedEvent.class);
                    } catch (Exception e) {
                        logger.error("Failed to parse payload for event {}: {}", event.getEventId(), e.getMessage());
                    }
                } else {
                    payload = "{\"corrupted\": \"true\", \"quantity\": -99}";
                    messagePayload = payload;
                    logger.warn("Chaos: Corrupting payload for event {}", event.getEventId());
                }

                rabbitTemplate.convertAndSend(
                    RabbitConfig.EXCHANGE_NAME,
                    "order.placed",
                    messagePayload,
                    message -> {
                        message.getMessageProperties().setHeader("X-Sender-App", "OrderService");
                        message.getMessageProperties().setHeader("X-Auth-Token", "d2lkZ2V0X3NlY3JldF90b2tlbg==");
                        return message;
                    },
                    correlationData
                );

                if (scenario == org.example.orderservice.controller.ChaosScenario.DUPLICATE_MESSAGE) {
                    logger.warn("Chaos: Sending duplicate message for event {}", event.getEventId());
                    rabbitTemplate.convertAndSend(
                        RabbitConfig.EXCHANGE_NAME,
                        "order.placed",
                        messagePayload,
                        message -> {
                            message.getMessageProperties().setHeader("X-Sender-App", "OrderService");
                            message.getMessageProperties().setHeader("X-Auth-Token", "d2lkZ2V0X3NlY3JldF90b2tlbg==");
                            return message;
                        },
                        correlationData
                    );
                }
            } catch (Exception e) {
                logger.error("Error relaying event {}: {}", event.getId(), e.getMessage());
            }
        }
    }
}
//för att inte tappa bort var event som vi skapade så skrevs både ordern och eventet in i varsin tabell i samma transaktion
// med jämna mellanrum (5 sec mellanrum så startar den här outbox relay) går till db och säger finns det ngra events här som
//har statusen PENDING i outbox_event db och om den hittar ngt sådant så går den igenom och loopar igenom alla outbox_eventen
// och sen ska den skicka den med rabbitmq convertAndSend och om den lyckas med det så behöver vi också uppdatera db. Det gör
//den i updateStatus som körs (om vi får meddelande från den då updateras db) och uppdateras till PROCESED.
//Och skulle det vara så att den inte har markerats i databasen nästa gång den här timern löser ut så att den kör relaye events igen.
//Så är den ju fortfarande PENDING då i databasen, så då kommer den att försöka skicka den igen då?Så det är för att garantera att de skickas verkligen.
//Så att går någonting fel där, så har den ju då inte uppdaterats, men den kan ju skickas för den bygger ju på då ett least ones delivery här.
//Så då är det viktigt att mottagaren, hanterar det att meddelandet kan kan faktiskt komma flera gånger. Då har vi det här unika uuid på den.För att skydda mot det!
//Så det ska ju då vara ett säkrare sätt i och med att vi gör den här första undanssparningen i en transaktion till en ACID kompatibel databas
//och sen kan vi kommentera det med en outbox relay implementation som kör med jämna mellanrum och försöker posta det som meddelande och då finns inte med tex rabbit kön uppe till exempel,
// ja, då får vi vänta, då får vi då har vi kvar våra pending events i vår tabell. Och sen kan vi skicka dem och publicera dem när meddelandekön är där och svarar och ger oss en aktiv knowledge tillbaka.
//ACID-kompatibel databas = databas som ger säkra, konsekventa transaktioner.