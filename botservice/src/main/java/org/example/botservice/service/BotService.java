package org.example.botservice.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Service
public class BotService {

    private final RestClient restClient;
    private final Random random = new Random();

    private static final List<String> JOKES = List.of(
            "Varför kan inte en cykel stå? För att den är tvåhjulig!",
            "Vad säger en räknare till en annan? Du kan räkna med mig!",
            "Vad heter en blind dinosaurie? Doyouthinkhesaurus!"
    );

    public BotService(@Value("${services.message.url}") String messageServiceUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(messageServiceUrl)
                .build();
    }

    public void handleEvent(Map<String, Object> event) {
        String senderId = (String) event.get("senderId");
        String content  = (String) event.get("content");

        if ("bot".equals(senderId)) return;
        if (content == null || !content.startsWith("!bot")) return;

        String command = content.substring(4).trim().toLowerCase();
        String reply   = generateReply(command);

        postReply(reply);
    }

    private String generateReply(String command) {
        return switch (command) {
            case "hej"   -> "Hej! Jag är ChatBot. Skriv !bot hjälp för kommandon.";
            case "hjälp" -> "Kommandon: !bot hej | !bot tid | !bot skämt";
            case "tid"   -> "Klockan är " + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
            case "skämt" -> JOKES.get(random.nextInt(JOKES.size()));
            default      -> "Okänt kommando. Skriv !bot hjälp för tillgängliga kommandon.";
        };
    }

    private void postReply(String content) {
        restClient.post()
                .uri("/api/messages")
                .body(Map.of("content", content, "senderId", "bot"))
                .retrieve()
                .toBodilessEntity();
    }
}