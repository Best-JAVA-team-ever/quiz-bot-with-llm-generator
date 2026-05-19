package com.quizbot.api.rest;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@RestController
public class HealthController {

    @GetMapping("/healthcheck")
    public Mono<Map<String, Object>> healthcheck() {
        return Mono.just(Map.of(
            "status", "UP",
            "authors", List.of("Best JAVA TEAM :)")
        ));
    }
}
