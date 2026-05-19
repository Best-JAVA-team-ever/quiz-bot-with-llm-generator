package com.quizbot.api.telegram;

import com.quizbot.core.domain.Question;
import com.quizbot.core.domain.QuizSchedule;
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
    
    private final Map<String, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    public QuizScheduler(ScheduleService scheduleService, 
                         UserService userService, 
                         QuizService quizService, 
                         QuizTelegramBot telegramBot,
                         TaskScheduler taskScheduler,
                         StatisticsService statisticsService,
                         LlmClient llmClient,
                         QuestionService questionService) {
        this.scheduleService = scheduleService;
        this.userService = userService;
        this.quizService = quizService;
        this.telegramBot = telegramBot;
        this.taskScheduler = taskScheduler;
        this.statisticsService = statisticsService;
        this.llmClient = llmClient;
        this.questionService = questionService;
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
            llmClient.suggestDifficultyUpdates(stats).thenAccept(updates -> {
                for (var update : updates) {
                    questionService.getQuestionById(update.questionId()).subscribe(q -> {
                        if (q != null && q.difficulty() != update.newDifficulty()) {
                            Question updated = q.withDifficulty(update.newDifficulty());
                            questionService.updateQuestion(updated).subscribe();
                            log.info("Updated difficulty for question {} to {}", q.id(), update.newDifficulty());
                        }
                    });
                }
            });
        });
    }

    @Scheduled(fixedRate = 60000)
    public void refreshSchedules() {
        scheduleService.getGlobalSchedule().subscribe(this::syncSchedule);
    }

    private void syncSchedule(QuizSchedule schedule) {
        String taskId = schedule.id();
        if (!schedule.isActive()) {
            ScheduledFuture<?> future = scheduledTasks.remove(taskId);
            if (future != null) {
                future.cancel(false);
                log.info("Cancelled schedule {}", taskId);
            }
            return;
        }

        ScheduledFuture<?> existing = scheduledTasks.get(taskId);
        if (existing == null) {
            scheduleTask(schedule);
        }
    }

    private void scheduleTask(QuizSchedule schedule) {
        try {
            ScheduledFuture<?> future = taskScheduler.schedule(() -> runScheduledQuiz(schedule), new CronTrigger(schedule.cronExpression()));
            scheduledTasks.put(schedule.id(), future);
            log.info("Scheduled new task {} with cron {}", schedule.id(), schedule.cronExpression());
        } catch (Exception e) {
            log.error("Failed to schedule task with cron: {}", schedule.cronExpression(), e);
        }
    }

    private void runScheduledQuiz(QuizSchedule schedule) {
        log.info("Executing scheduled quiz for schedule: {}", schedule.id());
        List<String> topics = schedule.topicName() != null ? List.of(schedule.topicName()) : List.of();
        userService.getAllUsers().subscribe(user -> {
            quizService.startQuiz(user.telegramId(), topics).subscribe(q -> {
                if (q != null) {
                    String message = formatQuestion(q);
                    telegramBot.sendText(user.telegramId(), "Автоматический вопрос дня!\n\n" + message);
                }
            });
        });
    }

    private String formatQuestion(Question q) {
        List<String> options = new java.util.ArrayList<>(q.wrongAnswers());
        options.add(q.correctAnswer());
        Collections.shuffle(options);
        
        StringBuilder sb = new StringBuilder();
        sb.append("Вопрос: ").append(q.text()).append("\n\n");
        for (int i = 0; i < options.size(); i++) {
            sb.append(i + 1).append(". ").append(options.get(i)).append("\n");
        }
        return sb.toString();
    }
}
