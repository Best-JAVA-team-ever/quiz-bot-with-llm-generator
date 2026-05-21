package com.quizbot.core.service;

import com.quizbot.core.domain.Question;
import reactor.core.publisher.Mono;

import java.util.List;

public interface QuizService {
    Mono<Question> startQuiz(Long userId, List<String> topics);
    Mono<Void> recordAnswer(Long userId, String questionId, String value, boolean isCorrect);
}
