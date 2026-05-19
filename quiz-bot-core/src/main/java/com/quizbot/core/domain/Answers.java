package com.quizbot.core.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("answers")
@CompoundIndexes({
    @CompoundIndex(name = "user_answered_idx", def = "{'userId': 1, 'answeredAt': -1}")
})
public record Answers(
        @Id String id,
        @Indexed String userId,
        @Indexed String questionId,
        String chosenAnswer,
        boolean isCorrect,
        Instant answeredAt
) {
    public static Answers create(String userId, String questionId,
                                  String chosenAnswer, boolean isCorrect) {
        return new Answers(null, userId, questionId, chosenAnswer, isCorrect, Instant.now());
    }
}