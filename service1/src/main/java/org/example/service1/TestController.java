package org.example.service1;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.time.Instant;

@RestController
public class TestController {

    @GetMapping("/api/test")
    public String test(){

        RestClient client = RestClient.create("http://localhost:8083/api/today");
        var instant = client.get().retrieve().body(Instant.class);

        return "Hello from service1!";
    }

}

