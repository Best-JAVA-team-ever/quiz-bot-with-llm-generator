package com.quizbot.core.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("llm_log")
@CompoundIndexes({
    @CompoundIndex(name = "success_at_idx", def = "{'isSuccess': 1, 'createdAt': 1}")
})
public record LlmLog(
        @Id String id,
        @Indexed String requestType,
        String prompt,
        String response,
        String model,
        @Indexed boolean isSuccess,
        String errorMessage,
        int durationMs,
        @Indexed Instant createdAt
) {
    public static LlmLog create(String requestType, String prompt, String response,
                                 String model, boolean isSuccess, String errorMessage,
                                 int durationMs) {
        return new LlmLog(null, requestType, prompt, response, model,
                isSuccess, errorMessage, durationMs, Instant.now());
    }
}