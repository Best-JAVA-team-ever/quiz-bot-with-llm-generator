package com.quizbot.core.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quizbot.core.domain.Question;
import com.quizbot.core.service.LlmLogService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.util.ArrayList;
import java.util.List;

@Service
public class GigaChatClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(GigaChatClient.class);

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final GigaChatAuthService authService;
    private final LlmLogService llmLogService;
    private final java.util.concurrent.atomic.AtomicLong lastRequestTime = new java.util.concurrent.atomic.AtomicLong(0);

    @Autowired
    public GigaChatClient(org.springframework.beans.factory.ObjectProvider<MeterRegistry> meterRegistryProvider,
                          GigaChatAuthService authService,
                          LlmLogService llmLogService) {
        this(meterRegistryProvider, authService, llmLogService, null);
    }

    public GigaChatClient(org.springframework.beans.factory.ObjectProvider<MeterRegistry> meterRegistryProvider,
                          GigaChatAuthService authService,
                          LlmLogService llmLogService,
                          WebClient webClient) {
        this.meterRegistry = meterRegistryProvider.getIfAvailable();
        this.authService = authService;
        this.llmLogService = llmLogService;
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
        return generateQuestionRecursive(topics, difficulty, 0);
    }

    private Mono<Question> generateQuestionRecursive(List<String> topics, Integer difficulty, int accumulatedTokens) {
        String prompt = String.format(
                "Сгенерируй вопрос для викторины на темы: %s. Сложность: %d по шкале от 1 до 5.\n" +
                        "ОТВЕТЬ СТРОГО В ФОРМАТЕ JSON БЕЗ ЛИШНЕГО ТЕКСТА И БЕЗ ВВОДНЫХ СЛОВ:\n" +
                        "{\"text\": \"...\", \"correctAnswer\":\"...\", \"incorrectAnswers\": [\"...\", \"...\", \"...\"]}",
                String.join(", ", topics), difficulty);

        return callGigaChatWithMetrics(prompt, "generate_question")
                .flatMap(response -> {
                    try {
                        JsonNode root = objectMapper.readTree(response);
                        int requestTokens = root.path("usage").path("total_tokens").asInt();
                        int totalTokens = accumulatedTokens + requestTokens;

                        if (totalTokens > 1000) {
                            return Mono.error(new RuntimeException("Сумма токенов для генерации превышена"));
                        }

                        String content = root.path("choices").get(0).path("message").path("content").asText();
                        String jsonOnly = extractJson(content);
                        JsonNode qNode = objectMapper.readTree(jsonOnly);

                        List<String> incorrect = new ArrayList<>();
                        qNode.path("incorrectAnswers").forEach(n -> incorrect.add(n.asText()));

                        if (qNode.path("text").asText().isEmpty() || qNode.path("correctAnswer").asText().isEmpty() || incorrect.size() < 3) {
                            throw new RuntimeException("Неполный ответ от модели");
                        }

                        return Mono.just(Question.create(
                                qNode.path("text").asText(),
                                qNode.path("correctAnswer").asText(),
                                incorrect,
                                difficulty,
                                null, null,
                                topics));
                    } catch (Exception e) {
                        log.warn("Failed to parse LLM response, retrying... Error: {}", e.getMessage());
                        // Получаем токены даже при ошибке парсинга контента, если это возможно
                        int used = 0;
                        try {
                            used = objectMapper.readTree(response).path("usage").path("total_tokens").asInt();
                        } catch (Exception ignored) {}
                        
                        return generateQuestionRecursive(topics, difficulty, accumulatedTokens + Math.max(used, 100));
                    }
                })
                .onErrorMap(e -> {
                    if (e.getMessage() != null && (e.getMessage().contains("превышена") || e.getMessage().contains("лимит"))) {
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
        if (text == null) return "";
        int startObj = text.indexOf("{");
        int startArr = text.indexOf("[");
        int start;
        
        if (startObj != -1 && startArr != -1) start = Math.min(startObj, startArr);
        else if (startObj != -1) start = startObj;
        else if (startArr != -1) start = startArr;
        else return text;
        
        int endObj = text.lastIndexOf("}");
        int endArr = text.lastIndexOf("]");
        int end;
        
        if (endObj != -1 && endArr != -1) end = Math.max(endObj, endArr);
        else if (endObj != -1) end = endObj;
        else if (endArr != -1) end = endArr;
        else return text;

        if (end > start) {
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
                        String jsonOnly = extractJson(content);

                        JsonNode arrayNode = objectMapper.readTree(jsonOnly);
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
                long startTime = System.currentTimeMillis();
                
                return callGigaChat(prompt, token)
                        .flatMap(res -> {
                            long duration = System.currentTimeMillis() - startTime;
                            if (sample != null) {
                                sample.stop(meterRegistry.timer("llm.api.duration", "operation", operation, "status", "success"));
                                meterRegistry.counter("llm.api.calls", "operation", operation, "status", "success").increment();
                            }
                            return llmLogService.record(operation, prompt, res, "GigaChat", true, null, (int) duration)
                                    .thenReturn(res);
                        })
                        .onErrorResume(err -> {
                            long duration = System.currentTimeMillis() - startTime;
                            if (sample != null) {
                                sample.stop(meterRegistry.timer("llm.api.duration", "operation", operation, "status", "error"));
                            }
                            return llmLogService.record(operation, prompt, null, "GigaChat", false, err.getMessage(), (int) duration)
                                    .then(Mono.error(err));
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
