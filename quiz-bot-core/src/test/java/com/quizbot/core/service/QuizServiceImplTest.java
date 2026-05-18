package com.quizbot.core.service;

import com.quizbot.core.domain.Answer;
import com.quizbot.core.domain.Question;
import com.quizbot.core.repository.AnswerRepository;
import com.quizbot.core.repository.QuestionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.List;

class QuizServiceImplTest {

    private QuizServiceImpl quizService;
    private Flux<Question> mockQuestions;
    private Flux<Answer> mockAnswers;

    @BeforeEach
    void setUp() {
        QuestionRepository questionRepository = (QuestionRepository) Proxy.newProxyInstance(
                QuizServiceImplTest.class.getClassLoader(),
                new Class[]{QuestionRepository.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("findAll")) {
                        return mockQuestions;
                    }
                    return null;
                }
        );

        AnswerRepository answerRepository = (AnswerRepository) Proxy.newProxyInstance(
                QuizServiceImplTest.class.getClassLoader(),
                new Class[]{AnswerRepository.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("findByUserId")) {
                        return mockAnswers;
                    }
                    return null;
                }
        );

        quizService = new QuizServiceImpl(questionRepository, answerRepository);
    }

    @Test
    void startQuiz_shouldFilterOutCorrectlyAnsweredQuestions() {
        Question q1 = new Question("q1", "Question 1", "A", List.of("B", "C"), 1, List.of("Math"), null, null);
        Question q2 = new Question("q2", "Question 2", "D", List.of("E", "F"), 2, List.of("Math"), null, null);

        Answer a1 = new Answer("a1", "q1", 1L, "A", true, LocalDateTime.now());

        mockQuestions = Flux.just(q1, q2);
        mockAnswers = Flux.just(a1);

        StepVerifier.create(quizService.startQuiz(1L, null))
                .expectNextMatches(q -> q.id().equals("q2"))
                .verifyComplete();
    }

    @Test
    void startQuiz_shouldSortByDifficulty() {
        Question q1 = new Question("q1", "Hard Question", "A", List.of("B", "C"), 5, List.of("Math"), null, null);
        Question q2 = new Question("q2", "Easy Question", "D", List.of("E", "F"), 1, List.of("Math"), null, null);
        Question q3 = new Question("q3", "Medium1", "X", List.of("Y", "Z"), 2, List.of("Math"), null, null);
        Question q4 = new Question("q4", "Medium2", "X", List.of("Y", "Z"), 3, List.of("Math"), null, null);

        mockQuestions = Flux.just(q1, q2, q3, q4);
        mockAnswers = Flux.empty();

        StepVerifier.create(quizService.startQuiz(1L, null))
                .expectNextMatches(q -> q.difficulty() <= 3) 
                .verifyComplete();
    }
}
