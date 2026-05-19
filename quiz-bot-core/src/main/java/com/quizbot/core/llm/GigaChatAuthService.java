package com.quizbot.core.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.time.Instant;
import java.util.UUID;

@Service
public class GigaChatAuthService {

    private static final Logger log = LoggerFactory.getLogger(GigaChatAuthService.class);

    private final WebClient authClient;
    private final String authKey;
    private final ObjectMapper objectMapper;

    private String cachedToken;
    private Instant expiryTime;

    public GigaChatAuthService(@Value("${gigachat.api.key}") String authKey) {
        this.authKey = authKey;
        this.objectMapper = new ObjectMapper();

        SslContext sslContext;
        try {
            sslContext = SslContextBuilder.forClient()
                    .trustManager(InsecureTrustManagerFactory.INSTANCE)
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create SSL Context for Auth", e);
        }

        HttpClient httpClient = HttpClient.create().secure(t -> t.sslContext(sslContext));

        this.authClient = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .baseUrl("https://ngw.devices.sberbank.ru:9443/api/v2")
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .defaultHeader("Authorization", "Bearer " + authKey) // GigaChat docs say "Bearer <AuthKey>" for OAuth if using AuthKey, or Basic if using ClientID/Secret
                .build();
        
        // Note: For Personal API (PERS), the key from Studio is used in "Authorization: Bearer <AuthKey>" 
        // to get the actual Access Token.
    }

    public Mono<String> getAccessToken() {
        if (cachedToken != null && expiryTime != null && Instant.now().isBefore(expiryTime.minusSeconds(60))) {
            return Mono.just(cachedToken);
        }

        return fetchNewToken();
    }

    private Mono<String> fetchNewToken() {
        log.info("Fetching new GigaChat Access Token...");
        
        // MultiValueMap is needed for form-urlencoded in WebClient
        org.springframework.util.MultiValueMap<String, String> body = new org.springframework.util.LinkedMultiValueMap<>();
        body.add("scope", "GIGACHAT_API_PERS");

        return authClient.post()
                .uri("/oauth")
                .header("RqUID", UUID.randomUUID().toString())
                .body(org.springframework.web.reactive.function.BodyInserters.fromFormData(body))
                .retrieve()
                .bodyToMono(String.class)
                .map(response -> {
                    try {
                        JsonNode node = objectMapper.readTree(response);
                        this.cachedToken = node.path("access_token").asText();
                        long expiresAt = node.path("expires_at").asLong();
                        this.expiryTime = Instant.ofEpochMilli(expiresAt);
                        log.info("New GigaChat token obtained. Expires at: {}", expiryTime);
                        return cachedToken;
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to parse GigaChat token", e);
                    }
                })
                .onErrorMap(e -> {
                    log.error("GigaChat Authentication failed: {}", e.getMessage());
                    return new RuntimeException("Ошибка авторизации в GigaChat: " + e.getMessage(), e);
                });
    }
}
