package com.quizbot.core.service;

import com.quizbot.core.domain.Topic;
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

    private TopicServiceImpl topicService;

    @BeforeEach
    void setUp() {
        topicService = new TopicServiceImpl(topicRepository);
    }

    @Test
    void addTopic_shouldSaveTopic_whenNameIsValid() {
        Topic saved = new Topic("1", "Math");
        when(topicRepository.existsByName("Math")).thenReturn(Mono.just(false));
        when(topicRepository.save(any(Topic.class))).thenReturn(Mono.just(saved));

        StepVerifier.create(topicService.addTopic("Math"))
                .expectNext(saved)
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
        when(topicRepository.existsByName("Math")).thenReturn(Mono.just(true));

        StepVerifier.create(topicService.addTopic("Math"))
                .expectErrorMatches(e -> e instanceof IllegalArgumentException &&
                        e.getMessage().contains("уже существует"))
                .verify();
    }

    @Test
    void deleteTopic_shouldDeleteFoundTopic() {
        Topic topic = new Topic("1", "Math");
        when(topicRepository.findByName("Math")).thenReturn(Mono.just(topic));
        when(topicRepository.delete(topic)).thenReturn(Mono.empty());

        StepVerifier.create(topicService.deleteTopic("Math"))
                .verifyComplete();
    }

    @Test
    void exists_shouldDelegateToRepository() {
        when(topicRepository.existsByName("Math")).thenReturn(Mono.just(true));

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
