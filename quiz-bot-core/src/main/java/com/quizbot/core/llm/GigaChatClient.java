package com.quizbot.core.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quizbot.core.domain.Question;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class GigaChatClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(GigaChatClient.class);

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final GigaChatAuthService authService;
    private final java.util.concurrent.atomic.AtomicLong lastRequestTime = new java.util.concurrent.atomic.AtomicLong(0);

    public GigaChatClient(org.springframework.beans.factory.ObjectProvider<MeterRegistry> meterRegistryProvider,
                          GigaChatAuthService authService) {
        this(meterRegistryProvider, authService, null);
    }

    public GigaChatClient(org.springframework.beans.factory.ObjectProvider<MeterRegistry> meterRegistryProvider,
                          GigaChatAuthService authService,
                          WebClient webClient) {
        this.meterRegistry = meterRegistryProvider.getIfAvailable();
        this.authService = authService;
        this.objectMapper = new ObjectMapper();

        if (webClient == null) {
            SslContext sslContext;
            try {
                sslContext = SslContextBuilder.forClient()
                        .trustManager(InsecureTrustManagerFactory.INSTANCE)
                        .build();
            } catch (Exception e) {
                throw new RuntimeException("Failed to create SSL Context", e);
            }

            HttpClient httpClient = HttpClient.create().secure(t -> t.sslContext(sslContext));

            this.webClient = WebClient.builder()
                    .clientConnector(new ReactorClientHttpConnector(httpClient))
                    .baseUrl("https://gigachat.devices.sberbank.ru/api/v1")
                    .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .build();
        } else {
            this.webClient = webClient;
        }
        log.info("GigaChatClient initialized.");
    }

    @Override
    public Mono<Question> generateQuestion(List<String> topics, Integer difficulty) {
        String prompt = String.format(
                "Сгенерируй вопрос для викторины на темы: %s. Сложность: %d по шкале от 1 до 5.\n" +
                        "ОТВЕТЬ СТРОГО В ФОРМАТЕ JSON БЕЗ ЛИШНЕГО ТЕКСТА И БЕЗ ВВОДНЫХ СЛОВ:\n" +
                        "{\"text\": \"...\", \"correctAnswer\":\"...\", \"incorrectAnswers\": [\"...\", \"...\", \"...\"]}",
                String.join(", ", topics), difficulty);

        return callGigaChatWithMetrics(prompt, "generate_question")
                .map(response -> {
                    try {
                        JsonNode root = objectMapper.readTree(response);
                        JsonNode usage = root.path("usage");
                        if (!usage.isMissingNode() && usage.path("total_tokens").asInt() > 1000) {
                            throw new RuntimeException("Превышен лимит запросов к ИИ для этой операции");
                        }
                        String content = root.path("choices").get(0).path("message").path("content").asText();

                        // Извлекаем JSON из текста, если модель добавила пояснения (например, "Вот ваш вопрос: ...")
                        String jsonOnly = extractJson(content);
                        JsonNode qNode = objectMapper.readTree(jsonOnly);
                        
                        List<String> incorrect = new ArrayList<>();
                        qNode.path("incorrectAnswers").forEach(n -> incorrect.add(n.asText()));

                        return Question.create(
                                qNode.path("text").asText(),
                                qNode.path("correctAnswer").asText(),
                                incorrect,
                                difficulty,
                                null, null,
                                topics);
                    } catch (Exception e) {
                        throw new RuntimeException("Ошибка парсинга ответа: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
                    }
                })
                .onErrorMap(e -> {
                    if (e.getMessage() != null && e.getMessage().contains("Превышен лимит")) {
                        return e;
                    }
                    log.error("Failed to generate question: {}", e.getMessage());
                    if (meterRegistry != null) {
                        meterRegistry.counter("llm.api.errors", "operation", "generate_question").increment();
                    }
                    return new RuntimeException("Ошибка генерации вопроса: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()), e);
                });
    }

    private String extractJson(String text) {
        int start = text.indexOf("{");
        int end = text.lastIndexOf("}");
        if (start != -1 && end != -1 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }

    @Override
    public Mono<String> generateExplanation(String questionText, String correctAnswer) {
        String prompt = String.format("Объясни, почему ответ '%s' является правильным для вопроса: '%s'. Будь краток.",
                correctAnswer, questionText);
        return callSimple(prompt, "generate_explanation");
    }

    @Override
    public Mono<String> generateHint(String questionText) {
        String prompt = String.format("Дай короткую и полезную подсказку для следующего вопроса: '%s'. " +
                "В подсказке НЕЛЬЗЯ называть сам правильный ответ. Ответь только текстом подсказки.",
                questionText);
        return callSimple(prompt, "generate_hint");
    }

    @Override
    public Mono<List<DifficultyUpdate>> suggestDifficultyUpdates(List<QuestionWithStats> stats) {
        if (stats.isEmpty()) {
            return Mono.just(new ArrayList<>());
        }

        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("Проанализируй статистику ответов на вопросы викторины и предложи новые уровни сложности (от 1 до 5).\n");
        promptBuilder.append("Формат ответа строго JSON массив объектов: [{\"questionId\": \"...\", \"newDifficulty\": ...}]\n\n");
        promptBuilder.append("Статистика:\n");
        for (QuestionWithStats stat : stats) {
            promptBuilder.append(String.format("ID: %s, Текст: %s, Всего ответов: %d, Правильных: %d, Текущая сложность: %d\n",
                    stat.question().id(), stat.question().text(), stat.totalCount(), stat.correctCount(), stat.question().difficulty()));
        }

        return callGigaChatWithMetrics(promptBuilder.toString(), "suggest_difficulty")
                .map(response -> {
                    try {
                        JsonNode root = objectMapper.readTree(response);
                        String content = root.path("choices").get(0).path("message").path("content").asText();

                        if (content.startsWith("```json")) {
                            content = content.substring(7);
                            if (content.endsWith("```")) {
                                content = content.substring(0, content.length() - 3);
                            }
                        }

                        JsonNode arrayNode = objectMapper.readTree(content);
                        List<DifficultyUpdate> updates = new ArrayList<>();
                        if (arrayNode.isArray()) {
                            for (JsonNode node : arrayNode) {
                                updates.add(new DifficultyUpdate(
                                        node.path("questionId").asText(),
                                        node.path("newDifficulty").asInt()));
                            }
                        }
                        return updates;
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .onErrorResume(e -> {
                    log.error("Failed to suggest difficulty updates: {}", e.getMessage());
                    if (meterRegistry != null) {
                        meterRegistry.counter("llm.api.errors", "operation", "suggest_difficulty").increment();
                    }
                    return Mono.just(new ArrayList<>());
                });
    }

    private Mono<String> callSimple(String prompt, String operation) {
        return callGigaChatWithMetrics(prompt, operation)
                .map(response -> {
                    try {
                        JsonNode root = objectMapper.readTree(response);
                        JsonNode usage = root.path("usage");
                        if (!usage.isMissingNode() && usage.path("total_tokens").asInt() > 1000) {
                            throw new RuntimeException("Превышен лимит запросов к ИИ для этой операции");
                        }
                        return root.path("choices").get(0).path("message").path("content").asText();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .onErrorResume(e -> {
                    if (e.getMessage() != null && e.getMessage().contains("Превышен лимит")) {
                        return Mono.error(e);
                    }
                    log.warn("Simple LLM generation failed for operation {}: {}", operation, e.getMessage());
                    if (meterRegistry != null) {
                        meterRegistry.counter("llm.api.errors", "operation", operation).increment();
                    }
                    return Mono.just("Не удалось сгенерировать текст (ошибка: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()) + ")");
                });
    }

    private Mono<String> callGigaChatWithMetrics(String prompt, String operation) {
        return authService.getAccessToken().flatMap(token -> 
            Mono.defer(() -> {
                log.debug("Sending prompt to GigaChat for operation: {}", operation);
                Timer.Sample sample = meterRegistry != null ? Timer.start(meterRegistry) : null;
                return callGigaChat(prompt, token)
                        .doOnSuccess(res -> {
                            if (sample != null) {
                                sample.stop(meterRegistry.timer("llm.api.duration", "operation", operation, "status", "success"));
                                meterRegistry.counter("llm.api.calls", "operation", operation, "status", "success").increment();
                            }
                        })
                        .doOnError(err -> {
                            if (sample != null) {
                                sample.stop(meterRegistry.timer("llm.api.duration", "operation", operation, "status", "error"));
                            }
                        });
            })
        );
    }

    private Mono<String> callGigaChat(String prompt, String token) {
        return Mono.defer(() -> {
            long now = System.currentTimeMillis();
            long waitTime = Math.max(0, lastRequestTime.get() + 1200 - now);
            lastRequestTime.set(now + waitTime);

            Map<String, Object> body = new java.util.HashMap<>();
            body.put("model", "GigaChat");

            List<Map<String, String>> messages = new java.util.ArrayList<>();
            Map<String, String> userMsg = new java.util.HashMap<>();
            userMsg.put("role", "user");
            userMsg.put("content", prompt);
            messages.add(userMsg);

            body.put("messages", messages);
            body.put("temperature", 0.7);

            return Mono.delay(java.time.Duration.ofMillis(waitTime))
                    .then(webClient.post()
                            .uri("/chat/completions")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .bodyValue(body)
                            .retrieve()
                            .bodyToMono(String.class)
                            .retryWhen(reactor.util.retry.Retry.backoff(3, java.time.Duration.ofSeconds(2))
                                    .filter(e -> e instanceof org.springframework.web.reactive.function.client.WebClientResponseException.TooManyRequests)));
        });
    }
}
