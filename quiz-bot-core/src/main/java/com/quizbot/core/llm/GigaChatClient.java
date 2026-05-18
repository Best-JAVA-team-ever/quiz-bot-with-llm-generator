package com.quizbot.core.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quizbot.core.domain.Question;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
public class GigaChatClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(GigaChatClient.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final MeterRegistry meterRegistry;

    public GigaChatClient(@Value("${gigachat.api.key}") String apiKey, MeterRegistry meterRegistry) {
        this.apiKey = apiKey;
        this.meterRegistry = meterRegistry;
        this.objectMapper = new ObjectMapper();
        this.restClient = RestClient.builder()
                .baseUrl("https://gigachat.devices.sberbank.ru/api/v1")
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
        log.info("GigaChatClient initialized.");
    }

    @Override
    public CompletableFuture<Question> generateQuestion(List<String> topics, Integer difficulty) {
        String prompt = String.format(
            "Сгенерируй вопрос для викторины на темы: %s. Сложность: %d по шкале от 1 до 5. " +
            "Ответ должен быть в формате JSON: {\"text\": \"...\", \"correctAnswer\": \"...\", \"incorrectAnswers\": [\"...\", \"...\", \"...\"]}",
            String.join(", ", topics), difficulty
        );

        return CompletableFuture.supplyAsync(() -> {
            try {
                String response = callGigaChatWithMetrics(prompt, "generate_question");
                JsonNode root = objectMapper.readTree(response);
                String content = root.path("choices").get(0).path("message").path("content").asText();
                
                JsonNode qNode = objectMapper.readTree(content);
                List<String> incorrect = new ArrayList<>();
                qNode.path("incorrectAnswers").forEach(n -> incorrect.add(n.asText()));

                return new Question(
                    null,
                    qNode.path("text").asText(),
                    qNode.path("correctAnswer").asText(),
                    incorrect,
                    difficulty,
                    topics,
                    null, null
                );
            } catch (Exception e) {
                log.error("Failed to generate question: {}", e.getMessage());
                meterRegistry.counter("llm.api.errors", "operation", "generate_question").increment();
                throw new RuntimeException("Ошибка генерации вопроса: " + e.getMessage(), e);
            }
        });
    }

    @Override
    public CompletableFuture<String> generateExplanation(String questionText, String correctAnswer) {
        String prompt = String.format("Объясни, почему ответ '%s' является правильным для вопроса: '%s'. Будь краток.", correctAnswer, questionText);
        return CompletableFuture.supplyAsync(() -> callSimple(prompt, "generate_explanation"));
    }

    @Override
    public CompletableFuture<String> generateHint(String questionText) {
        String prompt = String.format("Дай небольшую подсказку для вопроса: '%s', не называя правильный ответ.", questionText);
        return CompletableFuture.supplyAsync(() -> callSimple(prompt, "generate_hint"));
    }

    @Override
    public CompletableFuture<List<DifficultyUpdate>> suggestDifficultyUpdates(List<QuestionWithStats> stats) {
        if (stats.isEmpty()) return CompletableFuture.completedFuture(new ArrayList<>());

        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("Проанализируй статистику ответов на вопросы викторины и предложи новые уровни сложности (от 1 до 5).\n");
        promptBuilder.append("Формат ответа строго JSON массив объектов: [{\"questionId\": \"...\", \"newDifficulty\": ...}]\n\n");
        promptBuilder.append("Статистика:\n");
        for (QuestionWithStats stat : stats) {
            promptBuilder.append(String.format("ID: %s, Текст: %s, Всего ответов: %d, Правильных: %d, Текущая сложность: %d\n",
                    stat.question().id(), stat.question().text(), stat.totalCount(), stat.correctCount(), stat.question().difficulty()));
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                String response = callGigaChatWithMetrics(promptBuilder.toString(), "suggest_difficulty");
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
                                node.path("newDifficulty").asInt()
                        ));
                    }
                }
                return updates;
            } catch (Exception e) {
                log.error("Failed to suggest difficulty updates: {}", e.getMessage());
                meterRegistry.counter("llm.api.errors", "operation", "suggest_difficulty").increment();
                return new ArrayList<>();
            }
        });
    }

    private String callSimple(String prompt, String operation) {
        try {
            String response = callGigaChatWithMetrics(prompt, operation);
            JsonNode root = objectMapper.readTree(response);
            return root.path("choices").get(0).path("message").path("content").asText();
        } catch (Exception e) {
            log.warn("Simple LLM generation failed for operation {}: {}", operation, e.getMessage());
            meterRegistry.counter("llm.api.errors", "operation", operation).increment();
            return "Не удалось сгенерировать текст";
        }
    }

    private String callGigaChatWithMetrics(String prompt, String operation) {
        log.debug("Sending prompt to GigaChat for operation: {}", operation);
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            String result = callGigaChat(prompt);
            sample.stop(meterRegistry.timer("llm.api.duration", "operation", operation, "status", "success"));
            meterRegistry.counter("llm.api.calls", "operation", operation, "status", "success").increment();
            return result;
        } catch (Exception e) {
            sample.stop(meterRegistry.timer("llm.api.duration", "operation", operation, "status", "error"));
            throw e;
        }
    }

    private String callGigaChat(String prompt) {
        Map<String, Object> body = Map.of(
            "model", "GigaChat",
            "messages", List.of(Map.of("role", "user", "content", prompt)),
            "temperature", 0.7
        );

        return restClient.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);
    }
}
