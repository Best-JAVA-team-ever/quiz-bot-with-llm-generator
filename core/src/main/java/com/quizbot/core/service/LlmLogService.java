package com.quizbot.core.service;

import com.quizbot.core.domain.LlmLog;
import com.quizbot.core.repository.LlmLogRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Service
public class LlmLogService {

    private final LlmLogRepository llmLogRepository;

    public LlmLogService(LlmLogRepository llmLogRepository) {
        this.llmLogRepository = llmLogRepository;
    }

    public Mono<LlmLog> record(String requestType, String prompt, String response,
                               String model, boolean isSuccess, String errorMessage,
                               int durationMs) {
        LlmLog entry = LlmLog.create(requestType, prompt, response, model, isSuccess, errorMessage, durationMs);
        return llmLogRepository.save(entry);
    }

    public Flux<LlmLog> findErrors() {
        return llmLogRepository.findAllByIsSuccessFalseOrderByCreatedAtDesc();
    }

    public Flux<LlmLog> findByPeriod(Instant from, Instant to) {
        return llmLogRepository.findAllByCreatedAtBetween(from, to);
    }

    public Flux<LlmLog> findByRequestTypeAndPeriod(String requestType, Instant from, Instant to) {
        return llmLogRepository.findAllByRequestTypeAndCreatedAtBetween(requestType, from, to);
    }
}
