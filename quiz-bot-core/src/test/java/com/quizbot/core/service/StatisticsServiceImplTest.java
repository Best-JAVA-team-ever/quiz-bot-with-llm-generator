package com.quizbot.core.service;

import com.quizbot.core.domain.Answer;
import com.quizbot.core.domain.Group;
import com.quizbot.core.domain.Question;
import com.quizbot.core.repository.AnswerRepository;
import com.quizbot.core.repository.GroupRepository;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatisticsServiceImplTest {

    @Mock
    private AnswerRepository answerRepository;
    @Mock
    private QuestionRepository questionRepository;
    @Mock
    private GroupRepository groupRepository;

    private StatisticsServiceImpl statisticsService;

    @BeforeEach
    void setUp() {
        statisticsService = new StatisticsServiceImpl(answerRepository, questionRepository, groupRepository);
    }

    @Test
    void getUserStats_shouldCalculateGlobalStats() {
        Answer correct = new Answer("1", "q1", 1L, "A", true, LocalDateTime.now());
        Answer incorrect = new Answer("2", "q2", 1L, "B", false, LocalDateTime.now());
        when(answerRepository.findByUserId(1L)).thenReturn(Flux.just(correct, incorrect));

        StepVerifier.create(statisticsService.getUserStats(1L, null))
                .expectNextMatches(stats ->
                        (long) stats.get("total") == 2 &&
                        (long) stats.get("correct") == 1 &&
                        (long) stats.get("incorrect") == 1 &&
                        (double) stats.get("percentage") == 50.0
                )
                .verifyComplete();
    }

    @Test
    void getUserStats_shouldReturnZeroPercentage_whenNoAnswers() {
        when(answerRepository.findByUserId(1L)).thenReturn(Flux.empty());

        StepVerifier.create(statisticsService.getUserStats(1L, null))
                .expectNextMatches(stats ->
                        (long) stats.get("total") == 0 &&
                        (double) stats.get("percentage") == 0.0
                )
                .verifyComplete();
    }

    @Test
    void getUserStats_shouldFilterByTopic() {
        Question q1 = new Question("q1", "Q1", "A", List.of(), 1, List.of("Math"), null, null);
        Answer mathAnswer = new Answer("1", "q1", 1L, "A", true, LocalDateTime.now());
        Answer otherAnswer = new Answer("2", "q2", 1L, "B", false, LocalDateTime.now());

        when(answerRepository.findByUserId(1L)).thenReturn(Flux.just(mathAnswer, otherAnswer));
        when(questionRepository.findByTopicNamesContaining("Math")).thenReturn(Flux.just(q1));

        StepVerifier.create(statisticsService.getUserStats(1L, "Math"))
                .expectNextMatches(stats ->
                        (long) stats.get("total") == 1 &&
                        (long) stats.get("correct") == 1 &&
                        (double) stats.get("percentage") == 100.0
                )
                .verifyComplete();
    }

    @Test
    void getGroupStats_shouldAggregateAllMemberAnswers() {
        Group group = new Group("g1", "Group1", "link", new HashSet<>(Set.of(1L, 2L)));
        Answer a1 = new Answer("1", "q1", 1L, "A", true, LocalDateTime.now());
        Answer a2 = new Answer("2", "q2", 2L, "B", false, LocalDateTime.now());

        when(groupRepository.findById("g1")).thenReturn(Mono.just(group));
        when(answerRepository.findByUserId(1L)).thenReturn(Flux.just(a1));
        when(answerRepository.findByUserId(2L)).thenReturn(Flux.just(a2));

        StepVerifier.create(statisticsService.getGroupStats("g1"))
                .expectNextMatches(stats -> (long) stats.get("total") == 2)
                .verifyComplete();
    }

    @Test
    void getGroupStats_shouldReturnEmptyMap_whenGroupNotFound() {
        when(groupRepository.findById("missing")).thenReturn(Mono.empty());

        StepVerifier.create(statisticsService.getGroupStats("missing"))
                .expectNextMatches(Map::isEmpty)
                .verifyComplete();
    }

    @Test
    void resetUserStats_shouldDeleteAnswersByUserId() {
        when(answerRepository.deleteByUserId(1L)).thenReturn(Mono.empty());

        StepVerifier.create(statisticsService.resetUserStats(1L))
                .verifyComplete();
    }
}
