package com.quizbot.core.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

@Document(collection = "answers")
public record Answer(
    @Id String id,
    String questionId,
    Long userId,
    String answerValue,
    boolean isCorrect,
    LocalDateTime timestamp
) {}
