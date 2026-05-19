package com.quizbot.core.service;

import com.quizbot.core.domain.Answers;
import com.quizbot.core.domain.Group;
import com.quizbot.core.domain.GroupMember;
import com.quizbot.core.domain.Question;
import com.quizbot.core.domain.Users;
import com.quizbot.core.repository.AnswersRepository;
import com.quizbot.core.repository.GroupMemberRepository;
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

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatisticsServiceImplTest {

    @Mock
    private AnswersRepository answersRepository;
    @Mock
    private QuestionRepository questionRepository;
    @Mock
    private GroupRepository groupRepository;
    @Mock
    private GroupMemberRepository groupMemberRepository;
    @Mock
    private UserServiceImpl userService;

    private StatisticsServiceImpl statisticsService;

    @BeforeEach
    void setUp() {
        statisticsService = new StatisticsServiceImpl(answersRepository, questionRepository, groupRepository, groupMemberRepository, userService);
    }

    @Test
    void getUserStats_shouldCalculateGlobalStats() {
        Users user = Users.create(1L, null);
        Answers correct = Answers.create("1", "q1", "A", true);
        Answers incorrect = Answers.create("1", "q2", "B", false);

        when(userService.findById("1")).thenReturn(Mono.just(user));
        when(answersRepository.findAllByUserIdAndAnsweredAtAfter(eq("1"), any(Instant.class)))
                .thenReturn(Flux.just(correct, incorrect));

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
        Users user = Users.create(1L, null);

        when(userService.findById("1")).thenReturn(Mono.just(user));
        when(answersRepository.findAllByUserIdAndAnsweredAtAfter(eq("1"), any(Instant.class)))
                .thenReturn(Flux.empty());

        StepVerifier.create(statisticsService.getUserStats(1L, null))
                .expectNextMatches(stats ->
                        (long) stats.get("total") == 0 &&
                        (double) stats.get("percentage") == 0.0
                )
                .verifyComplete();
    }

    @Test
    void getUserStats_shouldFilterByTopic() {
        Users user = Users.create(1L, null);
        Question q1 = new Question("q1", "Q1", "A", List.of(), 1, null, null, List.of("Math"), Instant.now(), Instant.now(), null);
        Answers mathAnswer = Answers.create("1", "q1", "A", true);

        when(userService.findById("1")).thenReturn(Mono.just(user));
        when(questionRepository.findAllByTopicNamesContainingAndDeletedAtIsNull("Math")).thenReturn(Flux.just(q1));
        when(answersRepository.findAllByUserIdAndQuestionIdInAndAnsweredAtAfter(eq("1"), any(List.class), any(Instant.class)))
                .thenReturn(Flux.just(mathAnswer));

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
        Group group = new Group("g1", "Group1", "link", null, Instant.now(), null);
        GroupMember m1 = new GroupMember("m1", group.id(), "1", Instant.now(), null);
        GroupMember m2 = new GroupMember("m2", group.id(), "2", Instant.now(), null);
        Answers a1 = Answers.create("1", "q1", "A", true);
        Answers a2 = Answers.create("2", "q2", "B", false);

        when(groupRepository.findByIdAndDeletedAtIsNull("g1")).thenReturn(Mono.just(group));
        when(groupMemberRepository.findAllByGroupIdAndDeletedAtIsNull("g1")).thenReturn(Flux.just(m1, m2));
        when(answersRepository.findAllByUserIdIn(any(List.class))).thenReturn(Flux.just(a1, a2));

        StepVerifier.create(statisticsService.getGroupStats("g1"))
                .expectNextMatches(stats -> (long) stats.get("total") == 2)
                .verifyComplete();
    }

    @Test
    void getGroupStats_shouldReturnEmptyMap_whenGroupNotFound() {
        when(groupRepository.findByIdAndDeletedAtIsNull("missing")).thenReturn(Mono.empty());

        StepVerifier.create(statisticsService.getGroupStats("missing"))
                .expectNextMatches(Map::isEmpty)
                .verifyComplete();
    }

    @Test
    void resetUserStats_shouldResetScoreViaUserService() {
        when(userService.resetScore("1")).thenReturn(Mono.just(Users.create(1L, null)));

        StepVerifier.create(statisticsService.resetUserStats(1L))
                .verifyComplete();
    }
}
