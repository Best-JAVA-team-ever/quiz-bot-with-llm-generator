package com.quizbot.core.service;

import com.quizbot.core.domain.Question;
import com.quizbot.core.domain.Topic;
import com.quizbot.core.repository.QuestionRepository;
import com.quizbot.core.repository.TopicRepository;
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
    private TopicRepository topicRepository;

    private QuestionServiceImpl questionService;

    @BeforeEach
    void setUp() {
        questionService = new QuestionServiceImpl(questionRepository, topicRepository);
    }

    @Test
    void addQuestion_shouldSave_whenNoTopics() {
        Question q = new Question(null, "Q?", "A", List.of("B"), 1, List.of(), null, null);
        Question saved = new Question("id1", "Q?", "A", List.of("B"), 1, List.of(), null, null);
        when(questionRepository.save(q)).thenReturn(Mono.just(saved));

        StepVerifier.create(questionService.addQuestion(q))
                .expectNext(saved)
                .verifyComplete();
    }

    @Test
    void addQuestion_shouldCreateMissingTopics_andSaveQuestion() {
        Question q = new Question(null, "Q?", "A", List.of("B"), 1, List.of("NewTopic"), null, null);
        Question saved = new Question("id1", "Q?", "A", List.of("B"), 1, List.of("NewTopic"), null, null);

        when(topicRepository.existsByName("NewTopic")).thenReturn(Mono.just(false));
        when(topicRepository.save(any(Topic.class))).thenReturn(Mono.just(new Topic("t1", "NewTopic")));
        when(questionRepository.save(q)).thenReturn(Mono.just(saved));

        StepVerifier.create(questionService.addQuestion(q))
                .expectNext(saved)
                .verifyComplete();
    }

    @Test
    void addQuestion_shouldNotCreateTopic_whenAlreadyExists() {
        Question q = new Question(null, "Q?", "A", List.of("B"), 1, List.of("Math"), null, null);
        Question saved = new Question("id1", "Q?", "A", List.of("B"), 1, List.of("Math"), null, null);

        when(topicRepository.existsByName("Math")).thenReturn(Mono.just(true));
        when(questionRepository.save(q)).thenReturn(Mono.just(saved));

        StepVerifier.create(questionService.addQuestion(q))
                .expectNext(saved)
                .verifyComplete();
    }

    @Test
    void getAllQuestions_shouldReturnAllFromRepository() {
        Question q1 = new Question("1", "Q1", "A", List.of(), 1, List.of(), null, null);
        Question q2 = new Question("2", "Q2", "B", List.of(), 2, List.of(), null, null);
        when(questionRepository.findAll()).thenReturn(Flux.just(q1, q2));

        StepVerifier.create(questionService.getAllQuestions())
                .expectNext(q1, q2)
                .verifyComplete();
    }

    @Test
    void getQuestionsByTopic_shouldDelegateToRepository() {
        Question q = new Question("1", "Q1", "A", List.of(), 1, List.of("Math"), null, null);
        when(questionRepository.findByTopicNamesContaining("Math")).thenReturn(Flux.just(q));

        StepVerifier.create(questionService.getQuestionsByTopic("Math"))
                .expectNext(q)
                .verifyComplete();
    }

    @Test
    void deleteQuestion_shouldDelegateToRepository() {
        when(questionRepository.deleteById("id1")).thenReturn(Mono.empty());

        StepVerifier.create(questionService.deleteQuestion("id1"))
                .verifyComplete();
    }

    @Test
    void updateQuestion_shouldSaveAndReturnUpdated() {
        Question updated = new Question("1", "Updated", "A", List.of(), 3, List.of(), null, null);
        when(questionRepository.save(updated)).thenReturn(Mono.just(updated));

        StepVerifier.create(questionService.updateQuestion(updated))
                .expectNext(updated)
                .verifyComplete();
    }

    @Test
    void getQuestionById_shouldReturnQuestionFromRepository() {
        Question q = new Question("1", "Q1", "A", List.of(), 1, List.of(), null, null);
        when(questionRepository.findById("1")).thenReturn(Mono.just(q));

        StepVerifier.create(questionService.getQuestionById("1"))
                .expectNext(q)
                .verifyComplete();
    }
}
