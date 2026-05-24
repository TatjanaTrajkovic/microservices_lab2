//package org.example.service1;
//
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.web.bind.annotation.GetMapping;
//import org.springframework.web.bind.annotation.RequestParam;
//import org.springframework.web.bind.annotation.RestController;
//import org.springframework.web.client.RestClient;
//
//import java.time.Instant;
//
//@RestController
//public class TestController {
//
//    @GetMapping("/api/test")
//    public String test(){
//
//        RestClient client = RestClient.create("http://localhost:8083/api/today");
//        var instant = client.get().retrieve().body(Instant.class);
//
//        return "Hello from service1!" + instant;
//    }
//
//}


package org.example.service1;

import org.example.grpc.GreetingServiceGrpc;
import org.example.grpc.HelloRequest;
import org.example.grpc.HelloResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController {

    @Autowired
    GreetingServiceGrpc.GreetingServiceBlockingStub stub;

    @GetMapping("/api/test")
    public String test(@RequestParam(defaultValue = "Guest") String name) {
        HelloRequest request = HelloRequest.newBuilder()
                .setName(name)
                .build();

        HelloResponse response = stub.sayHello(request);

        return "Service 1 received gRPC response: " + response.getMessage();
    }
}

