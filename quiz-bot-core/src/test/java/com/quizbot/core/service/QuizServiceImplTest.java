// package com.quizbot.core.service;

import com.quizbot.core.domain.Answer;
import com.quizbot.core.domain.Question;
import com.quizbot.core.repository.AnswerRepository;
import com.quizbot.core.repository.QuestionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuizServiceImplTest {

    @Mock
    private QuestionRepository questionRepository;
    @Mock
    private AnswerRepository answerRepository;

    private QuizServiceImpl quizService;

    @BeforeEach
    void setUp() {
        quizService = new QuizServiceImpl(questionRepository, answerRepository);
    }

    @Test
    void startQuiz_shouldFilterOutCorrectlyAnsweredQuestions() {
        Question q1 = new Question("q1", "Q1", "A", List.of("B", "C"), 1, List.of("Math"), null, null);
        Question q2 = new Question("q2", "Q2", "D", List.of("E", "F"), 2, List.of("Math"), null, null);
        Answer a1 = new Answer("a1", "q1", 1L, "A", true, LocalDateTime.now());

        when(questionRepository.findAll()).thenReturn(Flux.just(q1, q2));
        when(answerRepository.findByUserId(1L)).thenReturn(Flux.just(a1));

//         StepVerifier.create(quizService.startQuiz(1L, null))
//                 .expectNextMatches(q -> q.id().equals("q2"))
//                 .verifyComplete();
//     }

    @Test
    void startQuiz_shouldSortByDifficulty() {
        // 4 questions: 3 easy (difficulty 1) and 1 hard (difficulty 5).
        // Shuffle picks from top-3 by difficulty (all difficulty 1), so result is always easy.
        Question easy1 = new Question("q1", "Easy1", "A", List.of("B", "C"), 1, List.of("Math"), null, null);
        Question easy2 = new Question("q2", "Easy2", "D", List.of("E", "F"), 1, List.of("Math"), null, null);
        Question easy3 = new Question("q3", "Easy3", "X", List.of("Y", "Z"), 1, List.of("Math"), null, null);
        Question hard = new Question("q4", "Hard",  "H", List.of("I", "J"), 5, List.of("Math"), null, null);

        when(questionRepository.findAll()).thenReturn(Flux.just(hard, easy1, easy2, easy3));
        when(answerRepository.findByUserId(1L)).thenReturn(Flux.empty());

        StepVerifier.create(quizService.startQuiz(1L, null))
                .expectNextMatches(q -> q.difficulty() == 1)
                .verifyComplete();
    }

    @Test
    void startQuiz_shouldReturnEmpty_whenAllQuestionsAnsweredCorrectly() {
        Question q1 = new Question("q1", "Q1", "A", List.of("B", "C"), 1, List.of("Math"), null, null);
        Answer a1 = new Answer("a1", "q1", 1L, "A", true, LocalDateTime.now());

        when(questionRepository.findAll()).thenReturn(Flux.just(q1));
        when(answerRepository.findByUserId(1L)).thenReturn(Flux.just(a1));

        StepVerifier.create(quizService.startQuiz(1L, null))
                .verifyComplete();
    }

    @Test
    void startQuiz_shouldReturnEmpty_whenNoQuestionsExist() {
        when(questionRepository.findAll()).thenReturn(Flux.empty());

        StepVerifier.create(quizService.startQuiz(1L, null))
                .verifyComplete();
    }

    @Test
    void startQuiz_withTopicFilter_shouldQueryByTopic() {
        Question q1 = new Question("q1", "Q1", "A", List.of("B", "C"), 1, List.of("Math"), null, null);

        when(questionRepository.findByTopicNamesContaining("Math")).thenReturn(Flux.just(q1));
        when(answerRepository.findByUserId(1L)).thenReturn(Flux.empty());

        StepVerifier.create(quizService.startQuiz(1L, List.of("Math")))
                .expectNextMatches(q -> q.id().equals("q1"))
                .verifyComplete();
    }

    @Test
    void recordAnswer_shouldSaveAnswerToRepository() {
        when(answerRepository.save(any(Answer.class))).thenReturn(Mono.just(
                new Answer("id", "q1", 1L, "A", true, LocalDateTime.now())
        ));

        StepVerifier.create(quizService.recordAnswer(1L, "q1", "A", true))
                .verifyComplete();
    }
}
