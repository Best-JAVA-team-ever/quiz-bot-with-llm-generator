package com.quizbot.core.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Document("questions")
public record Question(
        @Id String id,
        String text,
        String correctAnswer,
        List<String> wrongAnswers,
        @Indexed int difficulty,
        String explanation,
        String hint,
        @Indexed List<String> topicNames,
        Instant createdAt,
        Instant updatedAt,
        Instant deletedAt
) {
    // Фабричный метод для нового вопроса
    public static Question create(String text, String correctAnswer, List<String> wrongAnswers,
                                   int difficulty, String explanation, String hint,
                                   List<String> topicNames) {
        Instant now = Instant.now();
        return new Question(null, text, correctAnswer, wrongAnswers,
                difficulty, explanation, hint, topicNames, now, now, null);
    }

    public Question withText(String newText) {
        return new Question(id, newText, correctAnswer, wrongAnswers,
                difficulty, explanation, hint, topicNames, createdAt, Instant.now(), deletedAt);
    }

    public Question withCorrectAnswer(String newAnswer) {
        return new Question(id, text, newAnswer, wrongAnswers,
                difficulty, explanation, hint, topicNames, createdAt, Instant.now(), deletedAt);
    }

    public Question withWrongAnswers(List<String> newWrongAnswers) {
        return new Question(id, text, correctAnswer, newWrongAnswers,
                difficulty, explanation, hint, topicNames, createdAt, Instant.now(), deletedAt);
    }

    public Question withDifficulty(int newDifficulty) {
        return new Question(id, text, correctAnswer, wrongAnswers,
                newDifficulty, explanation, hint, topicNames, createdAt, Instant.now(), deletedAt);
    }

    public Question withExplanation(String newExplanation) {
        return new Question(id, text, correctAnswer, wrongAnswers,
                difficulty, newExplanation, hint, topicNames, createdAt, Instant.now(), deletedAt);
    }

    public Question withHint(String newHint) {
        return new Question(id, text, correctAnswer, wrongAnswers,
                difficulty, explanation, newHint, topicNames, createdAt, Instant.now(), deletedAt);
    }

    public Question withTopicNames(List<String> newTopicNames) {
        return new Question(id, text, correctAnswer, wrongAnswers,
                difficulty, explanation, hint, newTopicNames, createdAt, Instant.now(), deletedAt);
    }

    public Question markAsDeleted() {
        return new Question(id, text, correctAnswer, wrongAnswers,
                difficulty, explanation, hint, topicNames, createdAt, updatedAt, Instant.now());
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}