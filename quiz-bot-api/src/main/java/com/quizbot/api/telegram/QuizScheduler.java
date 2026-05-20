package com.quizbot.api.telegram;

import com.quizbot.api.dispatcher.BotResponse;
import com.quizbot.api.dispatcher.ConversationContext;
import com.quizbot.api.dispatcher.ConversationContextRepository;
import com.quizbot.api.dispatcher.UserState;
import com.quizbot.core.domain.Question;
import com.quizbot.core.domain.QuizSchedule;
import com.quizbot.core.service.GroupService;
import com.quizbot.core.service.QuestionService;
import com.quizbot.core.service.QuizService;
import com.quizbot.core.service.ScheduleService;
import com.quizbot.core.service.StatisticsService;
import com.quizbot.core.service.UserService;
import com.quizbot.core.llm.LlmClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Component
public class QuizScheduler {

    private static final Logger log = LoggerFactory.getLogger(QuizScheduler.class);
    private static final TimeZone MOSCOW_TZ = TimeZone.getTimeZone("Europe/Moscow");

    private final ScheduleService scheduleService;
    private final UserService userService;
    private final QuizService quizService;
    private final QuizTelegramBot telegramBot;
    private final TaskScheduler taskScheduler;
    private final StatisticsService statisticsService;
    private final LlmClient llmClient;
    private final QuestionService questionService;
    private final GroupService groupService;
    private final ConversationContextRepository contextRepository;

    private final Map<String, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();
    private final Map<String, String> scheduledCrons = new ConcurrentHashMap<>();
    private final Map<String, String> scheduledTopics = new ConcurrentHashMap<>();

    public QuizScheduler(ScheduleService scheduleService,
                         UserService userService,
                         QuizService quizService,
                         QuizTelegramBot telegramBot,
                         TaskScheduler taskScheduler,
                         StatisticsService statisticsService,
                         LlmClient llmClient,
                         QuestionService questionService,
                         GroupService groupService,
                         ConversationContextRepository contextRepository) {
        this.scheduleService = scheduleService;
        this.userService = userService;
        this.quizService = quizService;
        this.telegramBot = telegramBot;
        this.taskScheduler = taskScheduler;
        this.statisticsService = statisticsService;
        this.llmClient = llmClient;
        this.questionService = questionService;
        this.groupService = groupService;
        this.contextRepository = contextRepository;
    }

    @PostConstruct
    public void init() {
        log.info("Initializing QuizScheduler...");
        refreshSchedules();
    }

    @Scheduled(cron = "0 0 8 L * ?")
    public void runMonthlyDifficultyUpdate() {
        log.info("Starting monthly adaptive difficulty update...");
        statisticsService.getQuestionsWithStats().collectList().subscribe(stats -> {
            llmClient.suggestDifficultyUpdates(stats).subscribe(updates -> {
                for (var update : updates) {
                    questionService.getQuestionById(update.questionId()).subscribe(q -> {
                        if (q != null && q.difficulty() != update.newDifficulty()) {
                            int oldDiff = q.difficulty();
                            int newDiff = update.newDifficulty();
                            Question withNewDiff = q.withDifficulty(newDiff);
                            Mono<Question> withHintMono;
                            if (oldDiff <= 3 && newDiff >= 4) {
                                withHintMono = llmClient.generateHint(q.text())
                                        .map(hint -> withNewDiff.withHint(hint.isEmpty() ? null : hint))
                                        .onErrorReturn(withNewDiff);
                            } else if (oldDiff >= 4 && newDiff <= 3) {
                                withHintMono = Mono.just(withNewDiff.withHint(null));
                            } else {
                                withHintMono = Mono.just(withNewDiff);
                            }
                            withHintMono.flatMap(finalQ -> questionService.updateQuestion(finalQ))
                                    .subscribe(saved -> log.info("Updated difficulty for question {} to {}", saved.id(), newDiff));
                        }
                    });
                }
            });
        });
    }

    @Scheduled(fixedRate = 60000)
    public void refreshSchedules() {
        scheduleService.getGlobalSchedule().subscribe(this::syncSchedule);

        userService.getAllUsers()
                .flatMap(u -> groupService.getGroupsForUser(u.telegramId()))
                .map(group -> group.id())
                .distinct()
                .flatMap(groupId -> scheduleService.getGroupSchedule(groupId))
                .subscribe(this::syncSchedule);
    }

    private void syncSchedule(QuizSchedule schedule) {
        if (schedule == null) return;
        String taskId = schedule.id();

        if (!schedule.isActive()) {
            ScheduledFuture<?> future = scheduledTasks.remove(taskId);
            scheduledCrons.remove(taskId);
            scheduledTopics.remove(taskId);
            if (future != null) {
                future.cancel(false);
                log.info("Cancelled schedule {}", taskId);
            }
            return;
        }

        String existingCron = scheduledCrons.get(taskId);
        String existingTopic = scheduledTopics.get(taskId);
        if (existingCron != null
                && existingCron.equals(schedule.cronExpression())
                && Objects.equals(existingTopic, schedule.topicName())) {
            return;
        }

        ScheduledFuture<?> old = scheduledTasks.remove(taskId);
        scheduledCrons.remove(taskId);
        scheduledTopics.remove(taskId);
        if (old != null) {
            old.cancel(false);
            log.info("Rescheduling {} (cron: {} → {})", taskId, existingCron, schedule.cronExpression());
        }

        scheduleTask(schedule);
    }

    private void scheduleTask(QuizSchedule schedule) {
        try {
            ScheduledFuture<?> future = taskScheduler.schedule(
                    () -> runScheduledQuiz(schedule),
                    new CronTrigger(schedule.cronExpression(), MOSCOW_TZ));
            scheduledTasks.put(schedule.id(), future);
            scheduledCrons.put(schedule.id(), schedule.cronExpression());
            scheduledTopics.put(schedule.id(), schedule.topicName() != null ? schedule.topicName() : "");
            log.info("Scheduled task {} with cron {} (MSK)", schedule.id(), schedule.cronExpression());
        } catch (Exception e) {
            log.error("Failed to schedule task with cron: {}", schedule.cronExpression(), e);
        }
    }

    private void runScheduledQuiz(QuizSchedule schedule) {
        log.info("Executing scheduled quiz for schedule: {}", schedule.id());
        List<String> topics = schedule.topicName() != null ? List.of(schedule.topicName()) : List.of();

        if (schedule.id().startsWith("group:")) {
            String groupId = schedule.id().substring(6);
            groupService.findMembers(groupId).subscribe(
                    member -> sendScheduledQuestion(Long.parseLong(member.userId()), topics),
                    e -> log.error("Error fetching members for group {}: {}", groupId, e.getMessage()));
        } else {
            userService.findAllActive().subscribe(
                    user -> sendScheduledQuestion(user.telegramId(), topics),
                    e -> log.error("Error fetching active users: {}", e.getMessage()));
        }
    }

    private void sendScheduledQuestion(long telegramId, List<String> topics) {
        quizService.startQuiz(telegramId, topics)
                .flatMap(q -> {
                    List<String> options = new ArrayList<>(q.wrongAnswers());
                    options.add(q.correctAnswer());
                    Collections.shuffle(options);

                    return contextRepository.findById(telegramId)
                            .defaultIfEmpty(new ConversationContext(telegramId))
                            .flatMap(ctx -> {
                                ctx.setState(UserState.IN_QUIZ);
                                ctx.setActiveQuestion(q);
                                ctx.setCurrentOptions(options);
                                ctx.setPendingTopics(topics);
                                return contextRepository.save(ctx);
                            })
                            .doOnSuccess(ctx -> {
                                StringBuilder sb = new StringBuilder("Автоматический вопрос дня!\n\n");
                                sb.append("Темы: ").append(String.join(", ", q.topicNames())).append("\n");
                                sb.append("Вопрос: ").append(q.text());
                                if (q.hint() != null) {
                                    sb.append("\nПодсказка: <tg-spoiler>").append(q.hint()).append("</tg-spoiler>");
                                }

                                List<List<BotResponse.Button>> keyboard = new ArrayList<>();
                                for (int i = 0; i < options.size(); i++) {
                                    keyboard.add(List.of(new BotResponse.Button(options.get(i), "\\ans_" + i)));
                                }
                                keyboard.add(List.of(new BotResponse.Button("Закончить викторину", "\\cancel")));

                                telegramBot.sendResponse(telegramId, null, BotResponse.buttons(sb.toString(), keyboard));
                            });
                })
                .subscribe(ctx -> {}, e -> log.error("Error sending scheduled question to {}: {}", telegramId, e.getMessage()));
    }
}
