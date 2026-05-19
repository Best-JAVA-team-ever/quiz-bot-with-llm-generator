package com.quizbot.core.service;

import com.quizbot.core.domain.Answers;
import com.quizbot.core.domain.Question;
import com.quizbot.core.domain.Users;
import com.quizbot.core.repository.AnswersRepository;
import com.quizbot.core.repository.QuestionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuizServiceImplTest {

    @Mock
    private QuestionRepository questionRepository;
    @Mock
    private AnswersRepository answersRepository;
    @Mock
    private UserServiceImpl userService;

    private QuizServiceImpl quizService;

    @BeforeEach
    void setUp() {
        quizService = new QuizServiceImpl(questionRepository, answersRepository, userService);
    }

    @Test
    void startQuiz_shouldFilterOutCorrectlyAnsweredQuestions() {
        Question q1 = Question.create("Q1", "A", List.of("B", "C"), 1, null, null, List.of("Math"));
        Question q1Saved = new Question("q1", q1.text(), q1.correctAnswer(), q1.wrongAnswers(), q1.difficulty(), q1.explanation(), q1.hint(), q1.topicNames(), q1.createdAt(), q1.updatedAt(), q1.deletedAt());
        
        Question q2 = Question.create("Q2", "D", List.of("E", "F"), 2, null, null, List.of("Math"));
        Question q2Saved = new Question("q2", q2.text(), q2.correctAnswer(), q2.wrongAnswers(), q2.difficulty(), q2.explanation(), q2.hint(), q2.topicNames(), q2.createdAt(), q2.updatedAt(), q2.deletedAt());

        Users user = Users.create(1L, null);
        Answers a1 = Answers.create("1", "q1", "A", true);

        when(questionRepository.findAllByDeletedAtIsNull()).thenReturn(Flux.just(q1Saved, q2Saved));
        when(userService.findById("1")).thenReturn(Mono.just(user));
        when(answersRepository.findAllByUserIdAndAnsweredAtAfter(eq("1"), any(Instant.class))).thenReturn(Flux.just(a1));

        StepVerifier.create(quizService.startQuiz(1L, null))
                .expectNextMatches(q -> q.id().equals("q2"))
                .verifyComplete();
    }

    @Test
    void startQuiz_shouldSortByDifficulty() {
        Question easy1 = new Question("q1", "Easy1", "A", List.of("B", "C"), 1, null, null, List.of("Math"), Instant.now(), Instant.now(), null);
        Question easy2 = new Question("q2", "Easy2", "D", List.of("E", "F"), 1, null, null, List.of("Math"), Instant.now(), Instant.now(), null);
        Question easy3 = new Question("q3", "Easy3", "X", List.of("Y", "Z"), 1, null, null, List.of("Math"), Instant.now(), Instant.now(), null);
        Question hard = new Question("q4", "Hard",  "H", List.of("I", "J"), 5, null, null, List.of("Math"), Instant.now(), Instant.now(), null);

        Users user = Users.create(1L, null);

        when(questionRepository.findAllByDeletedAtIsNull()).thenReturn(Flux.just(hard, easy1, easy2, easy3));
        when(userService.findById("1")).thenReturn(Mono.just(user));
        when(answersRepository.findAllByUserIdAndAnsweredAtAfter(eq("1"), any(Instant.class))).thenReturn(Flux.empty());

        StepVerifier.create(quizService.startQuiz(1L, null))
                .expectNextMatches(q -> q.difficulty() == 1)
                .verifyComplete();
    }

    @Test
    void startQuiz_shouldReturnEmpty_whenAllQuestionsAnsweredCorrectly() {
        Question q1 = new Question("q1", "Q1", "A", List.of("B", "C"), 1, null, null, List.of("Math"), Instant.now(), Instant.now(), null);
        Users user = Users.create(1L, null);
        Answers a1 = Answers.create("1", "q1", "A", true);

        when(questionRepository.findAllByDeletedAtIsNull()).thenReturn(Flux.just(q1));
        when(userService.findById("1")).thenReturn(Mono.just(user));
        when(answersRepository.findAllByUserIdAndAnsweredAtAfter(eq("1"), any(Instant.class))).thenReturn(Flux.just(a1));

        StepVerifier.create(quizService.startQuiz(1L, null))
                .verifyComplete();
    }

    @Test
    void startQuiz_shouldReturnEmpty_whenNoQuestionsExist() {
        Users user = Users.create(1L, null);
        when(questionRepository.findAllByDeletedAtIsNull()).thenReturn(Flux.empty());
        when(userService.findById("1")).thenReturn(Mono.just(user));

        StepVerifier.create(quizService.startQuiz(1L, null))
                .verifyComplete();
    }

    @Test
    void startQuiz_withTopicFilter_shouldQueryByTopic() {
        Question q1 = new Question("q1", "Q1", "A", List.of("B", "C"), 1, null, null, List.of("Math"), Instant.now(), Instant.now(), null);
        Users user = Users.create(1L, null);

        when(questionRepository.findAllByTopicNamesInAndDeletedAtIsNull(List.of("Math"))).thenReturn(Flux.just(q1));
        when(userService.findById("1")).thenReturn(Mono.just(user));
        when(answersRepository.findAllByUserIdAndAnsweredAtAfter(eq("1"), any(Instant.class))).thenReturn(Flux.empty());

        StepVerifier.create(quizService.startQuiz(1L, List.of("Math")))
                .expectNextMatches(q -> q.id().equals("q1"))
                .verifyComplete();
    }

    @Test
    void recordAnswer_shouldSaveAnswerToRepository() {
        Answers a1 = Answers.create("1", "q1", "A", true);
        when(answersRepository.save(any(Answers.class))).thenReturn(Mono.just(a1));

        StepVerifier.create(quizService.recordAnswer(1L, "q1", "A", true))
                .verifyComplete();
    }
}