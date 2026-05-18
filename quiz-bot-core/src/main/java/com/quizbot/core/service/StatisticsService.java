package com.quizbot.core.service;

import java.util.Map;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;

public interface StatisticsService {
    Mono<Map<String, Object>> getUserStats(Long userId, String topicName);
    Mono<Void> resetUserStats(Long userId);
    Mono<Map<String, Object>> getGroupStats(String groupId);
    Flux<com.quizbot.core.llm.LlmClient.QuestionWithStats> getQuestionsWithStats();
}
