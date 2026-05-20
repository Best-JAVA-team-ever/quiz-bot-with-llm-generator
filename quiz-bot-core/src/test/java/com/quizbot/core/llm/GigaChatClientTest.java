package com.quizbot.core.llm;

import com.quizbot.core.domain.Question;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GigaChatClientTest {

    @Mock
    private ObjectProvider<MeterRegistry> meterRegistryProvider;
    @Mock
    private GigaChatAuthService authService;
    @Mock
    private WebClient.Builder webClientBuilder;
    @Mock
    private WebClient webClient;
    @Mock
    private WebClient.RequestBodyUriSpec requestBodyUriSpec;
    @Mock
    private WebClient.RequestBodySpec requestBodySpec;
    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;
    @Mock
    private WebClient.ResponseSpec responseSpec;

    private GigaChatClient gigaChatClient;

    @BeforeEach
    void setUp() {
        when(webClientBuilder.clientConnector(any())).thenReturn(webClientBuilder);
        when(webClientBuilder.baseUrl(anyString())).thenReturn(webClientBuilder);
        when(webClientBuilder.defaultHeader(anyString(), any())).thenReturn(webClientBuilder);
        when(webClientBuilder.build()).thenReturn(webClient);
        
        gigaChatClient = new GigaChatClient(meterRegistryProvider, authService, webClientBuilder);
    }

    @Test
    void generateQuestion_shouldParseCorrectResponse() {
        String jsonResponse = "{\n" +
                "  \"choices\": [\n" +
                "    {\n" +
                "      \"message\": {\n" +
                "        \"content\": \"{\\\"text\\\": \\\"Test question?\\\", \\\"correctAnswer\\\": \\\"A\\\", \\\"incorrectAnswers\\\": [\\\"B\\\", \\\"C\\\", \\\"D\\\"]}\"\n" +
                "      }\n" +
                "    }\n" +
                "  ],\n" +
                "  \"usage\": {\n" +
                "    \"total_tokens\": 100\n" +
                "  }\n" +
                "}";

        when(authService.getAccessToken()).thenReturn(Mono.just("token"));
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(jsonResponse));

        StepVerifier.create(gigaChatClient.generateQuestion(List.of("Topic"), 3))
                .expectNextMatches(q -> q.text().equals("Test question?") && q.correctAnswer().equals("A"))
                .verifyComplete();
    }

    @Test
    void generateQuestion_shouldThrowError_whenTokenLimitExceeded() {
        String jsonResponse = "{\n" +
                "  \"choices\": [\n" +
                "    {\n" +
                "      \"message\": {\n" +
                "        \"content\": \"...\"\n" +
                "      }\n" +
                "    }\n" +
                "  ],\n" +
                "  \"usage\": {\n" +
                "    \"total_tokens\": 1500\n" +
                "  }\n" +
                "}";

        when(authService.getAccessToken()).thenReturn(Mono.just("token"));
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(jsonResponse));

        StepVerifier.create(gigaChatClient.generateQuestion(List.of("Topic"), 3))
                .expectErrorMatches(e -> e.getMessage().contains("Превышен лимит"))
                .verify();
    }
}
