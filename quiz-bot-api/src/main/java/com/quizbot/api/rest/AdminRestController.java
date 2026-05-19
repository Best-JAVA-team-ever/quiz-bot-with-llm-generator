package com.quizbot.api.rest;

import com.quizbot.core.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@RestController
public class AdminRestController {

    private final UserService userService;
    private final String adminApiKey;

    public AdminRestController(UserService userService, @Value("${admin.api.key:secret-key}") String adminApiKey) {
        this.userService = userService;
        this.adminApiKey = adminApiKey;
    }

    @GetMapping("/users")
    public Mono<ResponseEntity<Object>> getAllUsers(@RequestHeader(value = "X-API-KEY", required = false) String apiKey) {
        if (!adminApiKey.equals(apiKey)) {
            return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).body("Access Denied"));
        }

        return userService.getAllUsers()
                .map(u -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", u.telegramId());
                    map.put("role", u.role());
                    map.put("registeredAt", u.registeredAt().toString());
                    return map;
                })
                .collectList()
                .map(ResponseEntity::ok);
    }
}
