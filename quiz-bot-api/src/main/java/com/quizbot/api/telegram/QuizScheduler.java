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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Component
public class QuizScheduler {

    private static final Logger log = LoggerFactory.getLogger(QuizScheduler.class);

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
    private final Map<String, String> scheduledInfo = new ConcurrentHashMap<>();

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

    @Scheduled(fixedRate = 600000) // Раз в 10 минут
    public void heartbeat() {
        String schedulerStatus = "unknown";
        if (taskScheduler instanceof org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler tpts) {
            schedulerStatus = String.format("poolSize: %d, activeCount: %d", 
                tpts.getPoolSize(), tpts.getActiveCount());
        }
        log.info("QuizScheduler heartbeat: active tasks: {}, scheduler: {}", 
            scheduledTasks.size(), schedulerStatus);
    }

    @Scheduled(cron = "0 0 8 L * ?", zone = "Europe/Moscow")
    public void runMonthlyDifficultyUpdate() {
        log.info("Starting monthly adaptive difficulty update...");
        statisticsService.getQuestionsWithStats().collectList().subscribe(
            stats -> {
                llmClient.suggestDifficultyUpdates(stats).subscribe(
                    updates -> {
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
                    },
                    e -> log.error("Error getting difficulty updates: {}", e.getMessage())
                );
            },
            e -> log.error("Error getting question stats: {}", e.getMessage())
        );
    }

    @Scheduled(fixedRate = 60000) // Раз в минуту вполне достаточно
    public void refreshSchedules() {
        log.debug("Refreshing schedules...");
        
        scheduleService.getGlobalSchedule()
            .map(List::of)
            .defaultIfEmpty(List.of())
            .zipWith(groupService.getAllGroups()
                .flatMap(group -> scheduleService.getGroupSchedule(group.id()))
                .collectList()
                .defaultIfEmpty(List.of()))
            .subscribe(tuple -> {
                List<QuizSchedule> allDbSchedules = new java.util.ArrayList<>();
                allDbSchedules.addAll(tuple.getT1());
                allDbSchedules.addAll(tuple.getT2());
                
                java.util.Set<String> dbIds = allDbSchedules.stream()
                    .filter(QuizSchedule::isActive)
                    .map(QuizSchedule::id)
                    .collect(java.util.stream.Collectors.toSet());
                
                // Отменяем те, что есть в памяти, но нет в БД или стали неактивны
                for (String taskId : scheduledTasks.keySet()) {
                    if (!dbIds.contains(taskId)) {
                        ScheduledFuture<?> future = scheduledTasks.remove(taskId);
                        scheduledInfo.remove(taskId);
                        if (future != null) {
                            future.cancel(false);
                            log.info("Cancelled schedule {} (removed from DB or deactivated)", taskId);
                        }
                    }
                }
                
                // Синхронизируем активные
                for (QuizSchedule s : allDbSchedules) {
                    syncSchedule(s);
                }
            }, e -> log.error("Error refreshing schedules: {}", e.getMessage()));
    }

    private void syncSchedule(QuizSchedule schedule) {
        if (schedule == null || !schedule.isActive()) return;
        String taskId = schedule.id();

        String info = schedule.cronExpression() + "|" + (schedule.topicName() != null ? schedule.topicName() : "");
        String existingInfo = scheduledInfo.get(taskId);
        
        if (existingInfo != null && existingInfo.equals(info)) {
            return;
        }

        ScheduledFuture<?> old = scheduledTasks.remove(taskId);
        scheduledInfo.remove(taskId);
        if (old != null) {
            old.cancel(false);
            log.info("Rescheduling {} (changed: {} → {})", taskId, existingInfo, info);
        }

        scheduleTask(schedule, info);
    }

    private void scheduleTask(QuizSchedule schedule, String info) {
        try {
            ScheduledFuture<?> future = taskScheduler.schedule(
                    () -> runScheduledQuiz(schedule),
                    new CronTrigger(schedule.cronExpression(), java.util.TimeZone.getTimeZone("Europe/Moscow")));
            scheduledTasks.put(schedule.id(), future);
            scheduledInfo.put(schedule.id(), info);
            log.info("Scheduled task {} with cron {} and topic {}", 
                schedule.id(), schedule.cronExpression(), schedule.topicName());
        } catch (Exception e) {
            log.error("Failed to schedule task {}: {}", schedule.id(), e.getMessage());
        }
    }

    private void runScheduledQuiz(QuizSchedule schedule) {
        log.info("Executing scheduled quiz for schedule: {}", schedule.id());
        List<String> topics = schedule.topicName() != null ? List.of(schedule.topicName()) : List.of();

        if (schedule.id().startsWith("group:")) {
            String groupId = schedule.id().substring(6);
            log.info("Fetching members for group {}", groupId);
            groupService.findMembers(groupId)
                .flatMap(member -> sendScheduledQuestion(Long.parseLong(member.userId()), topics, true))
                .subscribe(
                    v -> {},
                    e -> log.error("Error executing group schedule {}: {}", groupId, e.getMessage()),
                    () -> log.info("Finished group schedule {}", groupId));
        } else {
            log.info("Fetching all active users for global schedule");
            userService.findAllActive()
                .flatMap(user -> sendScheduledQuestion(user.telegramId(), topics, false))
                .subscribe(
                    v -> {},
                    e -> log.error("Error executing global schedule: {}", e.getMessage()),
                    () -> log.info("Finished global schedule"));
        }
    }

    private Mono<Void> sendScheduledQuestion(long telegramId, List<String> topics, boolean isGroup) {
        log.debug("Preparing scheduled question for user {}, topics: {}, isGroup: {}", telegramId, topics, isGroup);
        return quizService.startQuiz(telegramId, topics)
            .flatMap(q -> contextRepository.findById(telegramId)
                .defaultIfEmpty(new ConversationContext(telegramId))
                .flatMap(ctx -> {
                    log.info("Picked question {} for user {}", q.id(), telegramId);
                    
                    List<String> options = new java.util.ArrayList<>();
                    if (q.wrongAnswers() != null) options.addAll(q.wrongAnswers());
                    options.add(q.correctAnswer());
                    Collections.shuffle(options);
                    
                    ctx.setState(UserState.IN_QUIZ);
                    ctx.setActiveQuestion(q);
                    ctx.setCurrentOptions(options);
                    ctx.setPendingTopics(topics);
                    
                    return contextRepository.save(ctx).then(Mono.fromRunnable(() -> {
                        BotResponse response = formatQuestion(q, options);
                        String header = (isGroup ? "Групповой вопрос!\n\n" : "Автоматический вопрос дня!\n\n");
                        BotResponse headeredResponse = new BotResponse(header + response.text(), response.keyboard(), false);
                        telegramBot.sendResponse(telegramId, null, headeredResponse);
                    }).subscribeOn(Schedulers.boundedElastic()));
                }))
            .switchIfEmpty(Mono.defer(() -> {
                log.info("No questions available for scheduled quiz for user {}", telegramId);
                String header = (isGroup ? "Групповой вопрос: " : "Автоматический вопрос дня: ");
                return Mono.fromRunnable(() -> telegramBot.sendResponse(telegramId, null, BotResponse.text(header + "Нет неотвеченных вопросов по выбранным темам.")))
                    .subscribeOn(Schedulers.boundedElastic());
            }))
            .onErrorResume(e -> {
                log.error("Error in sendScheduledQuestion for user {}: {}", telegramId, e.getMessage());
                return Mono.empty();
            }).then();
    }

    private BotResponse formatQuestion(Question q, List<String> options) {
        List<List<BotResponse.Button>> keyboard = new java.util.ArrayList<>();
        if (options != null) {
            for (int i = 0; i < options.size(); i++) {
                String optText = options.get(i);
                if (optText != null) {
                    keyboard.add(List.of(new BotResponse.Button(optText, "\\ans_" + i)));
                }
            }
        }
        keyboard.add(List.of(new BotResponse.Button("Закончить викторину", "\\cancel")));

        StringBuilder sb = new StringBuilder();
        List<String> tNames = q.topicNames();
        String topicsStr;
        if (tNames == null || tNames.isEmpty()) {
            topicsStr = "—";
        } else {
            topicsStr = tNames.stream().filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.joining(", "));
        }
        
        sb.append("Темы: ").append(escapeHtml(topicsStr)).append("\n");
        sb.append("Вопрос: ").append(escapeHtml(q.text() != null ? q.text() : "—")).append("\n");
        if (q.hint() != null && !q.hint().isEmpty()) {
            sb.append("Подсказка: <tg-spoiler>").append(escapeHtml(q.hint())).append("</tg-spoiler>\n");
        }
        sb.append("\n");

        return BotResponse.buttons(sb.toString(), keyboard);
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
