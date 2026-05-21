package com.quizbot.core.repository;

import com.quizbot.core.domain.LlmLog;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

import java.time.Instant;

@Repository
public interface LlmLogRepository extends ReactiveMongoRepository<LlmLog, String> {

    // Запись создаётся через унаследованный save() после каждого запроса к LLM.

    // Мониторинг ошибок LLM §3.5 — все неуспешные запросы
    Flux<LlmLog> findAllByIsSuccessFalseOrderByCreatedAtDesc();

    // Мониторинг использования LLM за период — количество запросов, задержки,
    // ошибки
    Flux<LlmLog> findAllByCreatedAtBetween(Instant from, Instant to);

    // Фильтрация по типу запроса — например, только генерация вопросов или только
    // подсказки
    Flux<LlmLog> findAllByRequestTypeAndCreatedAtBetween(String requestType, Instant from, Instant to);
}
