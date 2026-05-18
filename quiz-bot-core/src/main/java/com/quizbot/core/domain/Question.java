package com.quizbot.core.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.List;

@Document(collection = "questions")
public record Question(
    @Id String id,
    String text,
    String correctAnswer,
    List<String> incorrectAnswers,
    Integer difficulty,
    List<String> topicNames,
    String explanation,
    String hint
) {}
