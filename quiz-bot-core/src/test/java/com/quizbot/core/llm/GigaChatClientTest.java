package com.quizbot.core.llm;

import com.quizbot.core.domain.Question;
import com.quizbot.core.service.LlmLogService;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GigaChatClientTest {

    @Mock
    private GigaChatAuthService authService;

    @Mock
    private ObjectProvider<MeterRegistry> meterRegistryProvider;

    @Mock
    private LlmLogService llmLogService;

    @Mock
    private WebClient webClient;

    private GigaChatClient gigaChatClient;

    @BeforeEach
    void setUp() {
        when(meterRegistryProvider.getIfAvailable()).thenReturn(null);
        gigaChatClient = new GigaChatClient(meterRegistryProvider, authService, llmLogService, webClient);
        
        when(llmLogService.record(anyString(), anyString(), nullable(String.class), anyString(), anyBoolean(), nullable(String.class), anyInt()))
                .thenReturn(Mono.empty());
    }

    private void setupWebClientMock(String mockResponse) {
        WebClient.RequestBodyUriSpec requestBodyUriSpec = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestBodySpec requestBodySpec = mock(WebClient.RequestBodySpec.class);
        @SuppressWarnings("rawtypes")
        WebClient.RequestHeadersSpec requestHeadersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.header(anyString(), any())).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(any(Class.class))).thenReturn(Mono.just(mockResponse));
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
        setupWebClientMock(mockResponse);

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
        setupWebClientMock(mockResponse);

        StepVerifier.create(gigaChatClient.generateQuestion(List.of("Java"), 3))
                .expectErrorMatches(e -> e.getMessage().contains("Превышен лимит"))
                .verify();
    }
}
