package com.quizbot.api.dispatcher;

import com.quizbot.core.domain.Question;
import com.quizbot.core.domain.Role;
import com.quizbot.core.domain.Users;
import com.quizbot.core.llm.LlmClient;
import com.quizbot.core.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageDispatcherE2ETest {

    @Mock private UserService userService;
    @Mock private QuestionService questionService;
    @Mock private TopicService topicService;
    @Mock private QuizService quizService;
    @Mock private StatisticsService statisticsService;
    @Mock private LlmClient llmClient;
    @Mock private ScheduleService scheduleService;
    @Mock private GroupService groupService;
    @Mock private TelegramClient telegramClient;

    private MessageDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new MessageDispatcher(userService, questionService, topicService, quizService, 
                statisticsService, llmClient, scheduleService, groupService, telegramClient);
    }

    @Test
    void quizFlow_shouldWorkCorrectly() {
        Long userId = 123L;
        Users user = Users.create(userId, Role.USER);
        Question q = new Question("q1", "2+2?", "4", List.of("1", "2", "3"), 1, "Easy", null, List.of("Math"), 
                java.time.Instant.now(), java.time.Instant.now(), null);

        when(userService.getOrCreateUser(userId)).thenReturn(Mono.just(user));
        when(topicService.exists("Math")).thenReturn(Mono.just(true));
        when(quizService.startQuiz(eq(userId), any())).thenReturn(Mono.just(q));
        when(quizService.recordAnswer(anyLong(), anyString(), anyString(), anyBoolean())).thenReturn(Mono.empty());

        // 1. Start quiz
        StepVerifier.create(dispatcher.handleCommand(userId, "/quiz start Math"))
                .expectNextMatches(res -> res.contains("2+2?"))
                .verifyComplete();

        // 2. Answer correctly
        StepVerifier.create(dispatcher.handleCommand(userId, "4"))
                .expectNextMatches(res -> res.contains("Ответ корректный"))
                .verifyComplete();
    }

    @Test
    void addQuestionFlow_shouldWorkCorrectly() {
        Long adminId = 1L;
        Users admin = Users.create(adminId, Role.ADMIN);
        
        when(userService.getOrCreateUser(adminId)).thenReturn(Mono.just(admin));
        when(topicService.findOrCreate(any(), anyString())).thenReturn(Mono.just(com.quizbot.core.domain.Topic.create("General")));
        
        // 1. Start adding
        StepVerifier.create(dispatcher.handleCommand(adminId, "/add question General"))
                .expectNext("Введите текст вопроса:")
                .verifyComplete();
        
        // 2. Text
        StepVerifier.create(dispatcher.handleCommand(adminId, "What is Java?"))
                .expectNext("Введите текст правильного ответа:")
                .verifyComplete();

        // 3. Cancel
        StepVerifier.create(dispatcher.handleCommand(adminId, "/cancel"))
                .expectNext("Действие отменено")
                .verifyComplete();
    }
}
