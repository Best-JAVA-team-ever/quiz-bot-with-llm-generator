package com.quizbot.core.service;

import com.quizbot.core.domain.Question;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuestionServiceImplTest {

    @Mock
    private QuestionRepository questionRepository;
    @Mock
    private AnswersRepository answersRepository;
    @Mock
    private AuditLogService auditLogService;

    private QuestionServiceImpl questionService;

    @BeforeEach
    void setUp() {
        questionService = new QuestionServiceImpl(questionRepository, answersRepository, auditLogService);
    }

    @Test
    void addQuestion_shouldSave() {
        Question q = Question.create("Q?", "A", List.of("B"), 1, null, null, List.of());
        when(questionRepository.save(q)).thenReturn(Mono.just(q));

        StepVerifier.create(questionService.addQuestion(q))
                .expectNextMatches(saved -> saved.text().equals("Q?"))
                .verifyComplete();
    }

    @Test
    void getAllQuestions_shouldReturnAllFromRepository() {
        Question q1 = Question.create("Q1", "A", List.of(), 1, null, null, List.of());
        Question q2 = Question.create("Q2", "B", List.of(), 2, null, null, List.of());
        when(questionRepository.findAllByDeletedAtIsNull()).thenReturn(Flux.just(q1, q2));

        StepVerifier.create(questionService.getAllQuestions())
                .expectNext(q1, q2)
                .verifyComplete();
    }

    @Test
    void getQuestionsByTopic_shouldDelegateToRepository() {
        Question q = Question.create("Q1", "A", List.of(), 1, null, null, List.of("Math"));
        when(questionRepository.findAllByTopicNamesContainingAndDeletedAtIsNull("Math")).thenReturn(Flux.just(q));

        StepVerifier.create(questionService.getQuestionsByTopic("Math"))
                .expectNext(q)
                .verifyComplete();
    }

    @Test
    void deleteQuestion_shouldDelegateToRepository() {
        Question q = Question.create("Q1", "A", List.of(), 1, null, null, List.of());
        Question deleted = q.markAsDeleted();
        
        when(questionRepository.findByIdAndDeletedAtIsNull("id1")).thenReturn(Mono.just(q));
        when(questionRepository.save(any(Question.class))).thenReturn(Mono.just(deleted));

        StepVerifier.create(questionService.deleteQuestion("id1"))
                .verifyComplete();
    }

    @Test
    void updateQuestion_shouldSaveAndReturnUpdated() {
        Question existing = Question.create("Old", "A", List.of(), 1, null, null, List.of());
        Question updated = existing.withText("Updated").withDifficulty(3);

        when(questionRepository.findByIdAndDeletedAtIsNull(updated.id())).thenReturn(Mono.just(existing));
        when(questionRepository.save(any(Question.class))).thenReturn(Mono.just(updated));

        StepVerifier.create(questionService.updateQuestion(updated))
                .expectNextMatches(q -> q.text().equals("Updated") && q.difficulty() == 3)
                .verifyComplete();
    }

    @Test
    void getQuestionById_shouldReturnQuestionFromRepository() {
        Question q = Question.create("Q1", "A", List.of(), 1, null, null, List.of());
        when(questionRepository.findByIdAndDeletedAtIsNull("1")).thenReturn(Mono.just(q));

        StepVerifier.create(questionService.getQuestionById("1"))
                .expectNext(q)
                .verifyComplete();
    }
}