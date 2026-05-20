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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class MessageDispatcherE2ETest {

    @Mock private UserService userService;
    @Mock private QuestionService questionService;
    @Mock private TopicService topicService;
    @Mock private QuizService quizService;
    @Mock private StatisticsService statisticsService;
    @Mock private LlmClient llmClient;
    @Mock private ScheduleService scheduleService;
    @Mock private GroupService groupService;
    @Mock private TelegramClient telegramClient;
    @Mock private ConversationContextRepository contextRepository;

    private MessageDispatcher messageDispatcher;
    private final Long userId = 12345L;
    private final java.util.Map<Long, ConversationContext> contexts = new java.util.HashMap<>();

    @BeforeEach
    void setUp() {
        messageDispatcher = new MessageDispatcher(userService, questionService, topicService, quizService, statisticsService, llmClient, scheduleService, groupService, telegramClient, contextRepository);
        
        Users user = new Users(String.valueOf(userId), userId, Role.USER, true, java.time.Instant.now(), java.time.Instant.now(), null);
        when(userService.getOrCreateUser(userId)).thenReturn(Mono.just(user));
        
        when(contextRepository.findById(anyLong())).thenAnswer(i -> {
            Long id = i.getArgument(0);
            return Mono.justOrEmpty(contexts.get(id));
        });
        when(contextRepository.save(any())).thenAnswer(i -> {
            ConversationContext ctx = i.getArgument(0);
            contexts.put(ctx.getUserId(), ctx);
            return Mono.just(ctx);
        });
    }

    @Test
    void testQuizFlow() {
        Question q = Question.create("Test Q", "Ans", List.of("W1", "W2", "W3"), 1, "Exp", null, List.of("Java"));
        
        when(topicService.exists("Java")).thenReturn(Mono.just(true));
        when(quizService.startQuiz(eq(userId), anyList())).thenReturn(Mono.just(q));
        when(quizService.recordAnswer(eq(userId), nullable(String.class), eq("Ans"), eq(true))).thenReturn(Mono.empty());

        // 1. Start Quiz
        StepVerifier.create(messageDispatcher.handleCommand(userId, "\\quiz start Java"))
                .assertNext(res -> {
                    assertTrue(res.text().contains("Test Q"));
                    assertTrue(res.keyboard().size() > 0);
                })
                .verifyComplete();

        // 2. Answer Correctly
        // Simulation: recordAnswer is called, then nextQuizQuestion is called.
        // For simplicity in this mock-heavy test, we'll assume nextQuizQuestion returns empty (no more questions)
        when(quizService.startQuiz(eq(userId), anyList())).thenReturn(Mono.empty());

        StepVerifier.create(messageDispatcher.handleCommand(userId, "\\ans_Ans"))
                .assertNext(res -> {
                    System.out.println("Quiz answer response: " + res.text());
                    assertTrue(res.text().contains("Ответ корректный"));
                    assertTrue(res.text().contains("Нет неотвеченных вопросов"));
                })
                .verifyComplete();
    }

    @Test
    void testManualAddAndDeleteQuestion() {
        Users admin = new Users("admin", userId, Role.ADMIN, true, java.time.Instant.now(), java.time.Instant.now(), null);
        when(userService.getOrCreateUser(userId)).thenReturn(Mono.just(admin));
        when(topicService.exists(anyString())).thenReturn(Mono.just(true));

        // 1. Start Add Question
        StepVerifier.create(messageDispatcher.handleCommand(userId, "\\add question Java"))
                .assertNext(res -> {
                    System.out.println("Add question response: " + res.text());
                    assertTrue(res.text().contains("Введите текст вопроса"));
                })
                .verifyComplete();

        // 2. Enter Text
        StepVerifier.create(messageDispatcher.handleCommand(userId, "What is Java?"))
                .assertNext(res -> {
                    System.out.println("Enter text response: " + res.text());
                    assertTrue(res.text().contains("Введите текст правильного ответа"));
                })
                .verifyComplete();
        
        // Cancel add question before delete
        StepVerifier.create(messageDispatcher.handleCommand(userId, "\\cancel"))
                .assertNext(res -> assertTrue(res.text().contains("отменено")))
                .verifyComplete();

        // 3. Delete Question
        when(questionService.getQuestionById("q123")).thenReturn(Mono.just(mock(Question.class)));
        StepVerifier.create(messageDispatcher.handleCommand(userId, "\\delete question q123"))
                .assertNext(res -> {
                    System.out.println("Delete question prompt: " + res.text());
                    assertTrue(res.text().contains("Вы уверены"));
                })
                .verifyComplete();

        when(questionService.deleteQuestion("q123")).thenReturn(Mono.empty());
        StepVerifier.create(messageDispatcher.handleCommand(userId, "Да"))
                .assertNext(res -> {
                    System.out.println("Delete confirmation response: " + res.text());
                    assertTrue(res.text().contains("Вопросы удалены"));
                })
                .verifyComplete();
    }

    @Test
    void testLlmGenerationFlow() {
        Users admin = new Users("admin", userId, Role.ADMIN, true, java.time.Instant.now(), java.time.Instant.now(), null);
        when(userService.getOrCreateUser(userId)).thenReturn(Mono.just(admin));
        when(topicService.isValid(anyString())).thenReturn(true);

        // 1. Start Gen
        StepVerifier.create(messageDispatcher.handleCommand(userId, "\\add question gen Java"))
                .assertNext(res -> assertTrue(res.text().contains("Выберите уровень сложности")))
                .verifyComplete();

        // 2. Choose Difficulty -> Starts Generation
        Question generated = Question.create("Gen Q", "A", List.of("B", "C", "D"), 3, "Exp", null, List.of("Java"));
        when(llmClient.generateQuestion(anyList(), anyInt())).thenReturn(Mono.just(generated));
        when(llmClient.generateExplanation(anyString(), anyString())).thenReturn(Mono.just("Exp"));
        when(questionService.addQuestion(any())).thenReturn(Mono.just(generated));

        StepVerifier.create(messageDispatcher.handleCommand(userId, "3"))
                .assertNext(res -> assertTrue(res.text().contains("Вопрос успешно сгенерирован")))
                .verifyComplete();
    }
}
