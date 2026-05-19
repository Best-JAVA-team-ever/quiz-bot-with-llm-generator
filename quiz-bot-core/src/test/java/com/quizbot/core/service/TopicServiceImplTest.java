package com.quizbot.core.service;

import com.quizbot.core.domain.Topic;
import com.quizbot.core.repository.QuestionRepository;
import com.quizbot.core.repository.TopicRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TopicServiceImplTest {

    @Mock
    private TopicRepository topicRepository;
    @Mock
    private QuestionRepository questionRepository;
    @Mock
    private AuditLogService auditLogService;

    private TopicServiceImpl topicService;

    @BeforeEach
    void setUp() {
        topicService = new TopicServiceImpl(topicRepository, questionRepository, auditLogService);
    }

    @Test
    void addTopic_shouldSaveTopic_whenNameIsValid() {
        Topic saved = Topic.create("Math");
        when(topicRepository.findByNameAndDeletedAtIsNull("Math")).thenReturn(Mono.empty());
        when(topicRepository.save(any(Topic.class))).thenReturn(Mono.just(saved));

        StepVerifier.create(topicService.addTopic("Math"))
                .expectNextMatches(t -> t.name().equals("Math"))
                .verifyComplete();
    }

    @Test
    void addTopic_shouldReturnError_whenNameIsTooShort() {
        StepVerifier.create(topicService.addTopic("a"))
                .expectError(IllegalArgumentException.class)
                .verify();
    }

    @Test
    void addTopic_shouldReturnError_whenNameIsAll() {
        StepVerifier.create(topicService.addTopic("all"))
                .expectError(IllegalArgumentException.class)
                .verify();
    }

    @Test
    void addTopic_shouldReturnError_whenNameIsAllUpperCase() {
        StepVerifier.create(topicService.addTopic("ALL"))
                .expectError(IllegalArgumentException.class)
                .verify();
    }

    @Test
    void addTopic_shouldReturnError_whenTopicAlreadyExists() {
        Topic existing = Topic.create("Math");
        when(topicRepository.findByNameAndDeletedAtIsNull("Math")).thenReturn(Mono.just(existing));

        StepVerifier.create(topicService.addTopic("Math"))
                .expectErrorMatches(e -> e instanceof IllegalStateException &&
                        e.getMessage().contains("уже существует"))
                .verify();
    }

    @Test
    void deleteTopic_shouldDeleteFoundTopic() {
        Topic topic = Topic.create("Math");
        Topic deleted = topic.markAsDeleted();
        
        when(topicRepository.findByNameAndDeletedAtIsNull("Math")).thenReturn(Mono.just(topic));
        when(questionRepository.existsByTopicNamesContainingAndDeletedAtIsNull(topic.id())).thenReturn(Mono.just(false));
        when(topicRepository.save(any(Topic.class))).thenReturn(Mono.just(deleted));

        StepVerifier.create(topicService.deleteTopic("Math"))
                .verifyComplete();
    }

    @Test
    void exists_shouldDelegateToRepository() {
        Topic existing = Topic.create("Math");
        when(topicRepository.findByNameAndDeletedAtIsNull("Math")).thenReturn(Mono.just(existing));

        StepVerifier.create(topicService.exists("Math"))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void isValid_shouldReturnFalse_forNull() {
        assertFalse(topicService.isValid(null));
    }

    @Test
    void isValid_shouldReturnFalse_forSingleCharacter() {
        assertFalse(topicService.isValid("a"));
    }

    @Test
    void isValid_shouldReturnFalse_forAllKeyword() {
        assertFalse(topicService.isValid("all"));
        assertFalse(topicService.isValid("ALL"));
    }

    @Test
    void isValid_shouldReturnTrue_forValidLatinName() {
        assertTrue(topicService.isValid("Math"));
    }

    @Test
    void isValid_shouldReturnTrue_forValidCyrillicName() {
        assertTrue(topicService.isValid("История"));
    }
}