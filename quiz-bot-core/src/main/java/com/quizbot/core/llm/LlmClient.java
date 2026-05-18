package com.quizbot.core.llm;

import com.quizbot.core.domain.Question;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface LlmClient {
    CompletableFuture<Question> generateQuestion(List<String> topics, Integer difficulty);
    CompletableFuture<String> generateExplanation(String questionText, String correctAnswer);
    CompletableFuture<String> generateHint(String questionText);
    CompletableFuture<List<DifficultyUpdate>> suggestDifficultyUpdates(List<QuestionWithStats> stats);

    record QuestionWithStats(Question question, long correctCount, long totalCount) {}
    record DifficultyUpdate(String questionId, Integer newDifficulty) {}
}
