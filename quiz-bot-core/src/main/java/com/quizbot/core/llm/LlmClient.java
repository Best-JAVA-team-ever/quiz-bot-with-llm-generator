package com.quizbot.core.llm;

import com.quizbot.core.domain.Question;
import reactor.core.publisher.Mono;
import java.util.List;

public interface LlmClient {
    Mono<Question> generateQuestion(List<String> topics, Integer difficulty);
    Mono<String> generateExplanation(String questionText, String correctAnswer);
    Mono<String> generateHint(String questionText);
    Mono<List<DifficultyUpdate>> suggestDifficultyUpdates(List<QuestionWithStats> stats);

    record QuestionWithStats(Question question, long correctCount, long totalCount) {}
    record DifficultyUpdate(String questionId, Integer newDifficulty) {}
}