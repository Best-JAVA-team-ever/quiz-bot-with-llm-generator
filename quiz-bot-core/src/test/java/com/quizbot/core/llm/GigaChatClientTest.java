package com.quizbot.core.llm;

import com.quizbot.core.domain.Question;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GigaChatClientTest {

    @Mock
    private GigaChatAuthService authService;

    @Mock
    private ObjectProvider<MeterRegistry> meterRegistryProvider;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private WebClient webClient;

    private GigaChatClient gigaChatClient;

    @BeforeEach
    void setUp() {
        when(meterRegistryProvider.getIfAvailable()).thenReturn(null);
        gigaChatClient = new GigaChatClient(meterRegistryProvider, authService, webClient);
    }

    @Test
    void testGenerateQuestionSuccess() {
        String mockResponse = """
                {
                  "choices": [{
                    "message": {
                      "content": "{\\"text\\": \\"Test Question\\", \\"correctAnswer\\": \\"A\\", \\"incorrectAnswers\\": [\\"B\\", \\"C\\", \\"D\\"]}"
                    }
                  }],
                  "usage": { "total_tokens": 100 }
                }
                """;

        when(authService.getAccessToken()).thenReturn(Mono.just("test-token"));
        when(webClient.post()
                .uri(anyString())
                .header(anyString(), anyString())
                .bodyValue(any())
                .retrieve()
                .bodyToMono(String.class))
                .thenReturn(Mono.just(mockResponse));

        StepVerifier.create(gigaChatClient.generateQuestion(List.of("Java"), 3))
                .assertNext(q -> {
                    assertEquals("Test Question", q.text());
                    assertEquals("A", q.correctAnswer());
                    assertEquals(3, q.wrongAnswers().size());
                })
                .verifyComplete();
    }

    @Test
    void testGenerateQuestionTokenLimitExceeded() {
        String mockResponse = """
                {
                  "choices": [{
                    "message": { "content": "{}" }
                  }],
                  "usage": { "total_tokens": 1001 }
                }
                """;

        when(authService.getAccessToken()).thenReturn(Mono.just("test-token"));
        when(webClient.post()
                .uri(anyString())
                .header(anyString(), anyString())
                .bodyValue(any())
                .retrieve()
                .bodyToMono(String.class))
                .thenReturn(Mono.just(mockResponse));

        StepVerifier.create(gigaChatClient.generateQuestion(List.of("Java"), 3))
                .expectErrorMatches(e -> e.getMessage().contains("Превышен лимит"))
                .verify();
    }
}
