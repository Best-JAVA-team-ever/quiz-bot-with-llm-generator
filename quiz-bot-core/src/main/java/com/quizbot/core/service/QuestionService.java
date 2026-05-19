package com.quizbot.core.service;

import com.quizbot.core.domain.Question;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface QuestionService {
    Mono<Question> addQuestion(Question question);

    Flux<Question> getAllQuestions();

    Flux<Question> getQuestionsByTopic(String topicName);

    Mono<Void> deleteQuestion(String id);

    Mono<Void> deleteQuestionsByTopic(String topicName);

    Mono<Void> deleteAllQuestions();

    Mono<Question> updateQuestion(Question question);

    Mono<Question> getQuestionById(String id);
}
