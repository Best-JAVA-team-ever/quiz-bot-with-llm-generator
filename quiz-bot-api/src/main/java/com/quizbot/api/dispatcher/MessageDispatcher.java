package com.quizbot.api.dispatcher;

import com.quizbot.core.domain.Question;
import com.quizbot.core.domain.Users;
import com.quizbot.core.domain.Role;
import com.quizbot.core.domain.Topic;
import com.quizbot.core.domain.GroupMember;
import com.quizbot.core.llm.LlmClient;
import com.quizbot.core.service.GroupService;
import com.quizbot.core.service.QuestionService;
import com.quizbot.core.service.QuizService;
import com.quizbot.core.service.ScheduleService;
import com.quizbot.core.service.StatisticsService;
import com.quizbot.core.service.TopicService;
import com.quizbot.core.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class MessageDispatcher {
    private static final Logger log = LoggerFactory.getLogger(MessageDispatcher.class);

    private final UserService userService;
    private final QuestionService questionService;
    private final TopicService topicService;
    private final QuizService quizService;
    private final StatisticsService statisticsService;
    private final LlmClient llmClient;
    private final ScheduleService scheduleService;
    private final GroupService groupService;
    private final TelegramClient telegramClient;

    // In-memory state storage (for production, this might need to be in Redis/DB)
    private final Map<Long, ConversationContext> contexts = new HashMap<>();

    public MessageDispatcher(UserService userService, QuestionService questionService, TopicService topicService, QuizService quizService, StatisticsService statisticsService, LlmClient llmClient, ScheduleService scheduleService, GroupService groupService, TelegramClient telegramClient) {
        this.userService = userService;
        this.questionService = questionService;
        this.topicService = topicService;
        this.quizService = quizService;
        this.statisticsService = statisticsService;
        this.llmClient = llmClient;
        this.scheduleService = scheduleService;
        this.groupService = groupService;
        this.telegramClient = telegramClient;
    }

    public Mono<String> handleCommand(Long userId, String textIn) {
        String text = textIn.startsWith("/") ? "\\" + textIn.substring(1) : textIn;
        log.info("Received command from user {}: {}", userId, text);
        ConversationContext context = contexts.computeIfAbsent(userId, k -> new ConversationContext());

        if (text.equalsIgnoreCase("\\cancel")) {
            context.reset();
            return Mono.just("Действие отменено");
        }

        return userService.getOrCreateUser(userId).flatMap(user -> {
            if (context.getState() == UserState.IN_QUIZ) {
                return processQuizAnswer(userId, context, text);
            }
            if (context.getState() != UserState.IDLE) {
                return handleConversation(user, context, text);
            }

            boolean isAdmin = user.role() == Role.ADMIN;

            if (text.startsWith("\\upgrade") && isAdmin) return handleUpgrade(text);
            if (text.startsWith("\\add tag") && isAdmin) return handleAddTag(text);
            if (text.startsWith("\\add question gen") && isAdmin) return startGenerateQuestion(context, text);
            if (text.startsWith("\\add question") && isAdmin) return startAddQuestion(context, text);
            if (text.startsWith("\\update question") && isAdmin) return startUpdateQuestion(context, text);
            if (text.startsWith("\\update difficulty") && isAdmin) return handleUpdateDifficulty();
            if (text.startsWith("\\delete question") && isAdmin) return handleDeleteQuestion(context, text);
            if (text.startsWith("\\update tag") && isAdmin) return handleUpdateTag(userId, text);
            if (text.startsWith("\\delete tag") && isAdmin) return handleDeleteTag(text);
            if (text.startsWith("\\schedule") && isAdmin) return handleSchedule(text);
            if (text.startsWith("\\group") || text.startsWith("\\start join_")) return handleGroup(user, context, text);
            if (text.startsWith("\\get questions")) return handleGetQuestions(user, text);
            if (text.startsWith("\\quiz start")) return startQuiz(userId, context, text);
            if (text.startsWith("\\score")) return handleScore(userId, context, text);
            if (text.equalsIgnoreCase("\\help")) return Mono.just(handleHelp(user));

            return Mono.just("Неизвестная команда. Введите \\help для списка доступных команд.");
        });
    }

    private Mono<String> startQuiz(Long userId, ConversationContext context, String text) {
        String params = text.replace("\\quiz start", "").trim();
        List<String> topics = params.isEmpty() ? List.of() : Arrays.asList(params.split(" "));

        return Flux.fromIterable(topics)
                .flatMap(topicService::exists)
                .collectList()
                .flatMap(results -> {
                    if (results.contains(false))
                        return Mono.just("Одна из указанных тем не существует");
                    context.setState(UserState.IN_QUIZ);
                    context.setPendingTopics(topics);
                    return nextQuizQuestion(userId, context);
                });
    }

    private Mono<String> nextQuizQuestion(Long userId, ConversationContext context) {
        return quizService.startQuiz(userId, context.getPendingTopics())
                .flatMap(q -> {
                    context.setActiveQuestion(q);
                    List<String> options = new ArrayList<>(q.wrongAnswers());
                    options.add(q.correctAnswer());
                    Collections.shuffle(options);
                    context.setCurrentOptions(options);

                    StringBuilder sb = new StringBuilder();
                    sb.append("Темы: ").append(String.join(", ", q.topicNames())).append("\n");
                    sb.append("Вопрос: ").append(q.text()).append("\n\n");
                    for (int i = 0; i < options.size(); i++) {
                        sb.append(i + 1).append(". ").append(options.get(i)).append("\n");
                    }
                    sb.append("\nВыберите ответ (напишите текст ответа или используйте кнопки):");
                    return Mono.just(sb.toString());
                })
                .switchIfEmpty(Mono.defer(() -> {
                    context.reset();
                    return Mono.just("Нет неотвеченных вопросов");
                }));
    }

    private Mono<String> processQuizAnswer(Long userId, ConversationContext context, String text) {
        String cleaned = text.trim().replaceAll("[\\[\\]]", "");
        Question q = context.getActiveQuestion();

        if (cleaned.equalsIgnoreCase("Ок") || cleaned.equalsIgnoreCase("Ok") || cleaned.equalsIgnoreCase("Далее")) {
            return nextQuizQuestion(userId, context);
        }

        if (cleaned.equalsIgnoreCase("Объяснить") || cleaned.equalsIgnoreCase("Explain")) {
            return Mono.just("Пояснение: " + (q.explanation() != null ? q.explanation() : "нет") + "\n[Ок]");
        }

        final String initialAnswer = cleaned;
        List<String> options = context.getCurrentOptions();
        String answer = cleaned;

        // If it's not an exact match, check if it's a numeric index
        if (options != null && options.stream().noneMatch(o -> o.equalsIgnoreCase(initialAnswer))) {
            String numericPart = answer.replaceAll("[^0-9]", "");
            if (!numericPart.isEmpty() && answer.matches("^[0-9]+[.)]?$")) {
                try {
                    int index = Integer.parseInt(numericPart);
                    if (index > 0 && index <= options.size()) {
                        answer = options.get(index - 1);
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }

        boolean isCorrect = q.correctAnswer().equalsIgnoreCase(answer);

        return quizService.recordAnswer(userId, q.id(), answer, isCorrect).then(Mono.defer(() -> {
            if (isCorrect) {
                return nextQuizQuestion(userId, context).map(nextMsg -> "Ответ корректный\n\n" + nextMsg);
            } else {
                return Mono.just("Ответ некорректный\nКорректный ответ: " + q.correctAnswer() +
                        "\nПояснение: " + (q.explanation() != null ? q.explanation() : "нет") +
                        "\n[Ок] [Объяснить]");
            }
        }));
    }

    private Mono<String> performGeneration(ConversationContext context) {
        return llmClient.generateQuestion(context.getPendingTopics(), context.getDifficulty())
                .timeout(java.time.Duration.ofSeconds(20))
                .flatMap(q -> {
                    // Делаем запросы последовательно (flatMap вместо zip), чтобы не спамить API
                    return llmClient.generateExplanation(q.text(), q.correctAnswer())
                            .flatMap(exp -> {
                                Mono<String> hintMono = q.difficulty() > 3
                                        ? llmClient.generateHint(q.text())
                                        : Mono.just("");
                                return hintMono.map(hint -> new java.util.AbstractMap.SimpleEntry<>(exp, hint));
                            })
                            .flatMap(entry -> {
                                String exp = entry.getKey();
                                String hint = entry.getValue();

                                Question finalQ = Question.create(
                                        q.text(), q.correctAnswer(), q.wrongAnswers(),
                                        q.difficulty(), exp, hint.isEmpty() ? null : hint,
                                        q.topicNames());

                                return questionService.addQuestion(finalQ).map(saved -> {
                                    context.reset();
                                    StringBuilder sb = new StringBuilder("Вопрос успешно сгенерирован\n");
                                    sb.append("Вопрос: ").append(saved.text()).append("\n");
                                    sb.append("Правильный ответ: ").append(saved.correctAnswer()).append("\n");
                                    sb.append("Неправильные ответы: ").append(String.join(", ", saved.wrongAnswers()))
                                            .append("\n");
                                    sb.append("Пояснение: ").append(saved.explanation()).append("\n");
                                    if (saved.hint() != null)
                                        sb.append("Подсказка: ").append(saved.hint());
                                    return sb.toString();
                                });
                            });
                })
                .onErrorResume(e -> {
                    if (e instanceof java.util.concurrent.TimeoutException) {
                        context.setState(UserState.AWAITING_GENERATION_TIMEOUT_CONTINUE);
                        return Mono.just(
                                "Время генерации превысило допустимое значение. Продолжить генерацию? [Да] [Нет]");
                    }
                    String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                    return Mono.just("Генерация прервана по причине: " + errorMsg);
                });
    }

    private Mono<String> handleGroup(Users user, ConversationContext context, String text) {
        boolean isAdmin = user.role() == Role.ADMIN;
        String params = text.replace("\\group", "").trim();

        if (isAdmin) {
            if (params.startsWith("create")) {
                String name = params.replace("create", "").trim();
                if (name.isEmpty())
                    return Mono.just("Использование: \\group create <название>");
                return groupService.createGroup(name).map(g -> String.format(
                        "Группа %s %s создана\nСсылка: %s", g.id(), g.name(), g.inviteLink()));
            }
            if (params.startsWith("invite")) {
                String[] parts = params.replace("invite", "").trim().split(" ");
                if (parts.length < 2) return Mono.just("Использование: \\group invite <ID группы> <ID пользователя>");
                String groupId = parts[0];
                long invitedUserId;
                try {
                    invitedUserId = Long.parseLong(parts[1]);
                } catch (NumberFormatException e) {
                    return Mono.just("Некорректный ID пользователя");
                }
                return groupService.addUserToGroup(groupId, invitedUserId)
                    .then(groupService.getGroup(groupId))
                    .map(g -> {
                        try {
                            telegramClient.execute(SendMessage.builder()
                                .chatId(String.valueOf(invitedUserId))
                                .text("Вы были добавлены в группу «" + g.name() + "»!")
                                .build());
                        } catch (TelegramApiException e) {
                            log.warn("Не удалось уведомить пользователя {}: {}", invitedUserId, e.getMessage());
                        }
                        return "Пользователь " + invitedUserId + " добавлен в группу «" + g.name() + "»";
                    })
                    .switchIfEmpty(Mono.just("Группа не найдена"));
            }
            if (params.startsWith("exclude")) {
                String[] parts = params.replace("exclude", "").trim().split(" ");
                if (parts.length < 2)
                    return Mono.just("Использование: \\group exclude <ID группы> <ID пользователя>");
                return groupService.removeUserFromGroup(parts[0], Long.parseLong(parts[1]))
                        .thenReturn("Пользователь удален из группы");
            }
            if (params.startsWith("delete")) {
                String groupId = params.replace("delete", "").trim();
                if (groupId.isEmpty())
                    return Mono.just("Использование: \\group delete <ID группы>");
                context.setTargetGroupId(groupId);
                context.setState(UserState.AWAITING_GROUP_DELETE_CONFIRMATION);
                return Mono.just("Вы уверены, что хотите удалить группу? [Да] [Нет]");
            }
            if (params.startsWith("schedule set")) {
                String[] parts = params.replace("schedule set", "").trim().split(" ", 2);
                if (parts.length < 2)
                    return Mono.just("Использование: \\group schedule set <ID группы> <cron-выражение>");
                try {
                    org.springframework.scheduling.support.CronExpression.parse(parts[1]);
                    return scheduleService.setGroupSchedule(parts[0], parts[1])
                            .thenReturn("Расписание для группы установлено: " + parts[1]);
                } catch (Exception e) {
                    return Mono.just("Некорректное cron-выражение");
                }
            }
            if (params.startsWith("schedule off")) {
                String groupId = params.replace("schedule off", "").trim();
                if (groupId.isEmpty())
                    return Mono.just("Использование: \\group schedule off <ID группы>");
                return scheduleService.disableGroupSchedule(groupId).thenReturn("Групповое расписание отключено");
            }
            if (params.equalsIgnoreCase("list")) {
                return groupService.getAllGroups()
                        .flatMap(g -> groupService.findMembers(g.id()).collectList().map(m -> 
                            g.id() + " " + g.name() + "\nУчастники: " + (m.isEmpty() ? "нет" : m.stream().map(GroupMember::userId).collect(Collectors.joining(", ")))
                        ))
                        .collect(Collectors.joining("\n\n"))
                        .map(s -> s.isEmpty() ? "Групп нет." : s);
            }
            if (params.equalsIgnoreCase("score") || params.equalsIgnoreCase("score_all")) {
                return groupService.getAllGroups()
                        .flatMap(g -> statisticsService.getGroupStats(g.id()).map(stats -> String.format(
                                "Группа %s: всего %d, верных %d (%.1f%%)",
                                g.name(), stats.get("total"), stats.get("correct"), stats.get("percentage"))))
                        .collect(Collectors.joining("\n"));
            }
        }

        if (params.equalsIgnoreCase("leave")) {
            return groupService.getGroupsForUser(user.telegramId()).collectList().map(myGroups -> {
                if (myGroups.isEmpty())
                    return "Вы не состоите в группах";
                return "Выберите группу, которую хотите покинуть:\n" + myGroups.stream()
                        .map(g -> "/group_leave_" + g.id() + " [" + g.name() + "]")
                        .collect(Collectors.joining("\n"));
            });
        }
        if (params.startsWith("leave_")) {
            String groupId = params.replace("leave_", "").trim();
            return groupService.removeUserFromGroup(groupId, user.telegramId()).thenReturn("Вы покинули группу");
        }
        if (params.equalsIgnoreCase("list")) {
            return groupService.getGroupsForUser(user.telegramId())
                    .flatMap(g -> groupService.findMembers(g.id()).collectList().map(m -> 
                        g.id() + " " + g.name() + "\nУчастники: " + (m.isEmpty() ? "нет" : m.stream().map(GroupMember::userId).collect(Collectors.joining(", ")))
                    ))
                    .collect(Collectors.joining("\n\n"))
                    .map(s -> s.isEmpty() ? "Вы не состоите в группах." : s);
        }
        if (params.equalsIgnoreCase("score")) {
            return groupService.getGroupsForUser(user.telegramId())
                    .flatMap(g -> statisticsService.getGroupStats(g.id()).map(stats -> String.format(
                            "Группа %s: всего %d, верных %d (%.1f%%)",
                            g.name(), stats.get("total"), stats.get("correct"), stats.get("percentage"))))
                    .collect(Collectors.joining("\n"));
        }
        if (text.startsWith("\\start join_")) {
            String inviteId = text.replace("\\start join_", "").trim();
            return groupService.getAllGroups()
                    .filter(g -> g.inviteLink().endsWith(inviteId))
                    .next()
                    .flatMap(g -> {
                        context.setTargetGroupId(g.id());
                        context.setState(UserState.AWAITING_GROUP_JOIN_CONFIRMATION);
                        return Mono.just("Вы хотите вступить в группу «" + g.name() + "»? [Да] [Нет]");
                    })
                    .switchIfEmpty(Mono.just("Приглашение недействительно"));
        }

        return Mono.just("Неизвестная подкоманда \\group");
    }

    private Mono<String> handleSchedule(String text) {
        String params = text.replace("\\schedule", "").trim();
        if (params.startsWith("set")) {
            String cron = params.replace("set", "").trim();
            try {
                org.springframework.scheduling.support.CronExpression.parse(cron);
                return scheduleService.setGlobalSchedule(cron, null).thenReturn("Расписание установлено: " + cron);
            } catch (Exception e) {
                return Mono.just("Некорректное cron-выражение");
            }
        } else if (params.equalsIgnoreCase("off")) {
            return scheduleService.disableGlobalSchedule().thenReturn("Автоматическая отправка отключена");
        } else if (params.equalsIgnoreCase("status")) {
            return scheduleService.getGlobalSchedule()
                    .map(s -> String.format("Состояние: %s\nCron: %s\nТема: %s",
                            s.isActive() ? "активно" : "отключено", s.cronExpression(), s.topicName() != null ? s.topicName() : "не задана"))
                    .switchIfEmpty(Mono.just("Глобальное расписание не установлено"));
        }
        return Mono.just("Использование: \\schedule <set cron|off|status>");
    }

    private Mono<String> handleScore(Long userId, ConversationContext context, String text) {
        if (text.equalsIgnoreCase("\\score reset")) {
            context.setState(UserState.AWAITING_SCORE_RESET_CONFIRMATION);
            return Mono.just("Вы уверены, что хотите сбросить свой счёт? [Да] [Нет]");
        }
        String topicName = text.replace("\\score", "").trim();
        if (!topicName.isEmpty()) {
            return topicService.exists(topicName).flatMap(exists -> {
                if (exists) return fetchStats(userId, topicName);
                return Mono.just("Темы " + topicName + " не существует");
            });
        }
        return fetchStats(userId, null);
    }

    private Mono<String> fetchStats(Long userId, String topicName) {
        return statisticsService.getUserStats(userId, topicName).map(stats -> String.format(
                "Статистика %s:\nОбщее количество отвеченных вопросов: %d\nПравильных ответов: %d\nНеправильных ответов: %d\nПроцент правильных ответов: %.1f%%",
                (topicName == null ? "общая" : "по теме " + topicName),
                stats.get("total"), stats.get("correct"), stats.get("incorrect"), stats.get("percentage")));
    }

    private Mono<String> handleUpgrade(String text) {
        String[] parts = text.split(" ");
        if (parts.length > 1) {
            try {
                Long targetId = Long.parseLong(parts[1]);
                return userService.upgradeUser(targetId).thenReturn("Пользователь " + targetId + " повышен до Admin");
            } catch (NumberFormatException e) {
                return Mono.just("Некорректный ID пользователя");
            }
        }
        return Mono.just("Использование: \\upgrade <ID>");
    }

    private Mono<String> handleAddTag(String text) {
        String tagName = text.replace("\\add tag", "").trim();
        return topicService.addTopic(tagName)
                .map(t -> "Тема " + tagName + " добавлена")
                .onErrorResume(e -> Mono.just(e.getMessage()));
    }

    private Mono<String> startGenerateQuestion(ConversationContext context, String text) {
        String params = text.replace("\\add question gen", "").trim();
        if (params.isEmpty())
            return Mono.just("Не было введено название темы");
        List<String> topics = Arrays.stream(params.split(" ")).filter(s -> !s.isBlank()).collect(Collectors.toList());
        context.setPendingTopics(topics);
        context.setState(UserState.AWAITING_GENERATION_DIFFICULTY);
        return Mono.just("Выберите уровень сложности: [1] [2] [3] [4] [5]");
    }

    private Mono<String> startAddQuestion(ConversationContext context, String text) {
        String params = text.replace("\\add question", "").trim();
        if (params.isEmpty())
            return Mono.just("Прервано добавление вопроса по причине: Не указаны темы");
        List<String> topics = Arrays.stream(params.split(" ")).filter(s -> !s.isBlank()).collect(Collectors.toList());

        return Flux.fromIterable(topics)
                .flatMap(t -> topicService.findOrCreate(null, t))
                .then(Mono.defer(() -> {
                    context.setPendingTopics(topics);
                    context.setState(UserState.AWAITING_QUESTION_TEXT);
                    return Mono.just("Введите текст вопроса:");
                }))
                .onErrorResume(e -> {
                    context.reset();
                    return Mono.just("Ошибка при создании темы: " + e.getMessage());
                });
    }

    private Mono<String> startUpdateQuestion(ConversationContext context, String text) {
        String id = text.replace("\\update question", "").trim();
        if (id.isEmpty())
            return Mono.just("Использование: \\update question <ID>");
        return questionService.getQuestionById(id).map(q -> {
            context.setPendingQuestion(q);
            context.setUpdateFieldIndex(0);
            context.setState(UserState.AWAITING_UPDATE_FIELD_CHOICE);
            return "Текущий текст: " + q.text() + "\nЖелаете изменить?\n[Изменить] [Оставить без изменений]";
        }).switchIfEmpty(Mono.just("Вопрос с таким ID не существует"));
    }

    private Mono<String> handleUpdateTag(Long userId, String text) {
        String params = text.replace("\\update tag", "").trim();
        String[] parts = params.split(" ", 2);
        if (parts.length < 2 || parts[0].isBlank() || parts[1].isBlank()) {
            return Mono.just("Использование: \\update tag <старое_название> <новое_название>");
        }
        String oldName = parts[0].trim();
        String newName = parts[1].trim();
        return topicService.rename(String.valueOf(userId), oldName, newName)
                .map(t -> "Тема \"" + oldName + "\" переименована в \"" + newName
                        + "\". Все связанные вопросы обновлены.")
                .onErrorResume(e -> Mono.just("Ошибка: " + e.getMessage()));
    }

    private Mono<String> handleDeleteTag(String text) {
        String tagName = text.replace("\\delete tag", "").trim();
        if (tagName.isEmpty())
            return Mono.just("Использование: \\delete tag <название>");
        return questionService.getQuestionsByTopic(tagName).collectList().flatMap(qs -> {
            if (!qs.isEmpty())
                return Mono.just("Нельзя удалить тему, так как по ней есть вопросы");
            return topicService.deleteTopic(tagName).thenReturn("Тема " + tagName + " удалена");
        });
    }

    private Mono<String> handleUpdateDifficulty() {
        return statisticsService.getQuestionsWithStats().collectList().flatMap(stats -> {
            if (stats.isEmpty())
                return Mono.just("Нет данных для анализа сложности.");
            llmClient.suggestDifficultyUpdates(stats).subscribe(updates -> {
                for (var update : updates) {
                    questionService.getQuestionById(update.questionId()).subscribe(q -> {
                        if (q != null && q.difficulty() != update.newDifficulty()) {
                            questionService.updateQuestion(q.withDifficulty(update.newDifficulty())).subscribe();
                        }
                    });
                }
            });
            return Mono.just(
                    "Запущен процесс адаптивного обновления уровней сложности. Это может занять некоторое время.");
        });
    }

    private Mono<String> handleDeleteQuestion(ConversationContext context, String text) {
        String param = text.replace("\\delete question", "").trim();
        if (param.isEmpty())
            return Mono.just("Использование: \\delete question <ID|Тема|all>");

        if (param.equalsIgnoreCase("all")) {
            context.setDeleteScope("all");
            context.setState(UserState.AWAITING_CONFIRMATION);
            return Mono.just("Вы уверены? [Да] [Нет]");
        } else {
            return questionService.getQuestionById(param).flatMap(q -> {
                context.setDeleteScope("id");
                context.setDeleteValue(param);
                context.setState(UserState.AWAITING_CONFIRMATION);
                return Mono.just("Вы уверены? [Да] [Нет]");
            }).switchIfEmpty(topicService.exists(param).flatMap(exists -> {
                if (exists) {
                    context.setDeleteScope("topic");
                    context.setDeleteValue(param);
                    context.setState(UserState.AWAITING_CONFIRMATION);
                    return Mono.just("Вы уверены? [Да] [Нет]");
                }
                return Mono.just("Данной темы не существует");
            }));
        }
    }

    private Mono<String> handleConversation(Users user, ConversationContext context, String text) {
        switch (context.getState()) {
            case AWAITING_GENERATION_DIFFICULTY:
                try {
                    int diff = Integer.parseInt(text);
                    if (diff < 1 || diff > 5)
                        throw new Exception();
                    context.setDifficulty(diff);
                    return performGeneration(context);
                } catch (Exception e) {
                    return Mono.just("Выберите уровень сложности от 1 до 5");
                }
            case AWAITING_GENERATION_TIMEOUT_CONTINUE:
                if (text.equalsIgnoreCase("Да"))
                    return performGeneration(context);
                context.reset();
                return Mono.just("Генерация прервана");
            case AWAITING_SCORE_RESET_CONFIRMATION:
                if (text.equalsIgnoreCase("Да")) {
                    return statisticsService.resetUserStats(user.telegramId()).then(Mono.defer(() -> {
                        context.reset();
                        return Mono.just("Счёт сброшен");
                    }));
                } else {
                    context.reset();
                    return Mono.just("Сброс отменён");
                }
            case AWAITING_GROUP_DELETE_CONFIRMATION:
                if (text.equalsIgnoreCase("Да")) {
                    String groupId = context.getTargetGroupId();
                    return groupService.deleteGroup(groupId).then(Mono.defer(() -> {
                        context.reset();
                        return Mono.just("Группа удалена");
                    }));
                } else {
                    context.reset();
                    return Mono.just("Удаление группы отменено");
                }
            case AWAITING_GROUP_JOIN_CONFIRMATION:
                if (text.equalsIgnoreCase("Да")) {
                    String groupId = context.getTargetGroupId();
                    return groupService.addUserToGroup(groupId, user.telegramId()).then(Mono.defer(() -> {
                        context.reset();
                        return Mono.just("Вы успешно вступили в группу!");
                    }));
                } else {
                    context.reset();
                    return Mono.just("Вступление в группу отменено");
                }
            case AWAITING_CONFIRMATION:
                if (text.equalsIgnoreCase("Да")) {
                    String scope = context.getDeleteScope();
                    Mono<Void> action = Mono.empty();
                    if ("all".equals(scope))
                        action = questionService.deleteAllQuestions();
                    else if ("id".equals(scope))
                        action = questionService.deleteQuestion(context.getDeleteValue());
                    else if ("topic".equals(scope))
                        action = questionService.deleteQuestionsByTopic(context.getDeleteValue());

                    return action.then(Mono.defer(() -> {
                        context.reset();
                        return Mono.just("Вопросы удалены");
                    }));
                } else {
                    context.reset();
                    return Mono.just("Удаление отменено");
                }
            case AWAITING_UPDATE_FIELD_CHOICE:
                if (text.equalsIgnoreCase("Изменить")) {
                    context.setState(UserState.AWAITING_UPDATE_NEW_VALUE);
                    return Mono.just("Введите новое значение:");
                } else if (text.equalsIgnoreCase("Оставить без изменений")) {
                    return nextUpdateField(context);
                } else
                    return Mono.just("Используйте кнопки [Изменить] или [Оставить без изменений]");
            case AWAITING_UPDATE_NEW_VALUE:
                return applyUpdateValue(context, text);
            case AWAITING_QUESTION_TEXT:
                if (!text.matches("^[а-яА-Яa-zA-Z0-9\\s\\.,!?;:\\-\"\\'()]{4,128}$")) {
                    context.reset();
                    return Mono.just("Прервано добавление вопроса по причине: некорректный текст вопроса");
                }
                context.setQuestionText(text);
                context.setState(UserState.AWAITING_CORRECT_ANSWER);
                return Mono.just("Введите текст правильного ответа:");
            case AWAITING_CORRECT_ANSWER:
                if (!isValidAnswer(text)) {
                    context.reset();
                    return Mono.just("Прервано добавление вопроса по причине: некорректный текст ответа");
                }
                context.setCorrectAnswer(text);
                context.setState(UserState.AWAITING_INCORRECT_ANSWER_1);
                return Mono.just("Введите текст неправильного ответа 1:");
            case AWAITING_INCORRECT_ANSWER_1:
            case AWAITING_INCORRECT_ANSWER_2:
                if (!isValidAnswer(text)) {
                    context.reset();
                    return Mono.just("Прервано добавление вопроса по причине: некорректный текст ответа");
                }
                context.getIncorrectAnswers().add(text);
                context.setState(context.getState() == UserState.AWAITING_INCORRECT_ANSWER_1
                        ? UserState.AWAITING_INCORRECT_ANSWER_2
                        : UserState.AWAITING_INCORRECT_ANSWER_3);
                return Mono.just("Введите текст неправильного ответа " +
                        (context.getState() == UserState.AWAITING_INCORRECT_ANSWER_2 ? "2:" : "3:"));
            case AWAITING_INCORRECT_ANSWER_3:
                if (!isValidAnswer(text)) {
                    context.reset();
                    return Mono.just("Прервано добавление вопроса по причине: некорректный текст ответа");
                }
                context.getIncorrectAnswers().add(text);
                context.setState(UserState.AWAITING_DIFFICULTY);
                return Mono.just("Выберите уровень сложности вопроса (1-5):");
            case AWAITING_DIFFICULTY:
                try {
                    int difficulty = Integer.parseInt(text);
                    if (difficulty < 1 || difficulty > 5)
                        throw new Exception();

                    String qText = context.getQuestionText();
                    String correctAns = context.getCorrectAnswer();
                    List<String> incorrectAnswers = new ArrayList<>(context.getIncorrectAnswers());
                    List<String> pendingTopics = context.getPendingTopics();

                    return llmClient.generateExplanation(qText, correctAns).zipWith(
                            difficulty > 3 ? llmClient.generateHint(qText) : Mono.just(""))
                            .flatMap(tuple -> {
                                // ИСПРАВЛЕНО: использован Question.create() вместо конструктора —
                                // правильный порядок полей и автоматически проставляются createdAt/updatedAt
                                Question q = Question.create(
                                        qText, correctAns, incorrectAnswers,
                                        difficulty, tuple.getT1(), tuple.getT2().isEmpty() ? null : tuple.getT2(),
                                        pendingTopics);
                                return questionService.addQuestion(q).map(saved -> {
                                    context.reset();
                                    StringBuilder sb = new StringBuilder();
                                    sb.append("Вопрос успешно добавлен под идентификатором ").append(saved.id())
                                            .append("\n");
                                    if (saved.explanation() != null)
                                        sb.append("Сгенерированное пояснение: ").append(saved.explanation())
                                                .append("\n");
                                    if (saved.hint() != null)
                                        sb.append("Сгенерированная подсказка: ").append(saved.hint());
                                    return sb.toString();
                                });
                            }).onErrorResume(ex -> {
                                // ИСПРАВЛЕНО: аналогично — Question.create() вместо конструктора
                                Question q = Question.create(
                                        qText, correctAns, incorrectAnswers,
                                        difficulty, "Пояснение недоступно (ошибка LLM)", null,
                                        pendingTopics);
                                return questionService.addQuestion(q).map(saved -> {
                                    context.reset();
                                    return "Вопрос успешно добавлен под идентификатором " + saved.id()
                                            + "\nСгенерированное пояснение: Пояснение недоступно (ошибка LLM)";
                                });
                            });
                } catch (Exception e) {
                    context.reset();
                    return Mono.just("Прервано добавление вопроса по причине: некорректный уровень сложности");
                }
            default:
                context.reset();
                return Mono.just("Ошибка состояния. Процесс прерван.");
        }
    }

    private Mono<String> nextUpdateField(ConversationContext context) {
        int index = context.getUpdateFieldIndex() + 1;
        context.setUpdateFieldIndex(index);
        Question q = context.getPendingQuestion();

        if (index == 1)
            return Mono.just("Текущий правильный ответ: " + q.correctAnswer()
                    + "\nЖелаете изменить?\n[Изменить] [Оставить без изменений]");
        if (index == 2)
            return Mono.just("Текущие неправильные ответы: " + String.join(", ", q.wrongAnswers())
                    + "\nЖелаете изменить?\n[Изменить] [Оставить без изменений]");
        if (index == 3)
            return Mono.just("Текущая сложность: " + q.difficulty()
                    + "\nЖелаете изменить?\n[Изменить] [Оставить без изменений]");
        if (index == 4)
            return Mono.just("Текущее пояснение: " + (q.explanation() != null ? q.explanation() : "нет")
                    + "\nЖелаете изменить?\n[Изменить] [Оставить без изменений]");

        return updateQuestionInDb(context);
    }

    private Mono<String> applyUpdateValue(ConversationContext context, String value) {
        int index = context.getUpdateFieldIndex();
        Question q = context.getPendingQuestion();

        if (index == 0) {
            if (!value.matches("^[а-яА-Яa-zA-Z0-9\\s\\.,!?;:\\-\"\\'()]{4,128}$"))
                return Mono.just("Некорректный формат текста вопроса. Попробуйте еще раз:");
            context.setPendingQuestion(q.withText(value));
        } else if (index == 1) {
            if (!isValidAnswer(value))
                return Mono.just("Некорректный формат ответа. Попробуйте еще раз:");
            context.setPendingQuestion(q.withCorrectAnswer(value));
        } else if (index == 2) {
            List<String> wrong = Arrays.stream(value.split(",")).map(String::trim).collect(Collectors.toList());
            if (wrong.size() != 3 || wrong.stream().anyMatch(a -> !isValidAnswer(a)))
                return Mono.just("Введите ровно 3 неправильных ответа через запятую:");
            context.setPendingQuestion(q.withWrongAnswers(wrong));
        } else if (index == 3) {
            try {
                int d = Integer.parseInt(value);
                if (d < 1 || d > 5)
                    throw new Exception();
                context.setPendingQuestion(q.withDifficulty(d));
            } catch (Exception e) {
                return Mono.just("Введите число от 1 до 5:");
            }
        } else if (index == 4) {
            context.setPendingQuestion(q.withExplanation(value));
        }

        return nextUpdateField(context);
    }

    private Mono<String> updateQuestionInDb(ConversationContext context) {
        return questionService.updateQuestion(context.getPendingQuestion())
                .map(saved -> {
                    context.reset();
                    return "Вопрос успешно обновлен";
                });
    }

    private boolean isValidAnswer(String text) {
        return text.matches("^[а-яА-Яa-zA-Z0-9\\s\\.,!?;:\\-\"\\'()]{1,64}$");
    }

    private String handleHelp(Users user) {
        StringBuilder sb = new StringBuilder("Доступные команды:\n");
        sb.append("\\quiz start [topics] — запуск викторины\n");
        sb.append("\\score [topic] — ваша статистика\n");
        sb.append("\\score reset — сброс статистики\n");
        sb.append("\\group list — список ваших групп\n");
        sb.append("\\group leave — покинуть группу\n");
        sb.append("\\group score — статистика ваших групп\n");
        sb.append("\\help — это сообщение\n");
        sb.append("\\cancel — отмена текущего действия\n");

        if (user.role() == Role.ADMIN) {
            sb.append("\nКоманды администратора:\n");
            sb.append("\\upgrade <ID> — повысить роль пользователя\n");
            sb.append("\\add tag <name> — добавить новую тему\n");
            sb.append("\\add question <topics> — добавить вопрос вручную\n");
            sb.append("\\add question gen <topics> — сгенерировать вопрос через LLM\n");
            sb.append("\\update question <ID> — изменить вопрос\n");
            sb.append("\\delete question <ID|Topic|all> — удалить вопрос(ы)\n");
            sb.append("\\update tag <old> <new> — переименовать тему\n");
            sb.append("\\delete tag <name> — удалить тему\n");
            sb.append("\\schedule set <cron> — настроить глобальное расписание\n");
            sb.append("\\schedule off — отключить глобальное расписание\n");
            sb.append("\\schedule status — проверить глобальное расписание\n");
            sb.append("\\group create <name> — создать группу\n");
            sb.append("\\group invite <groupID> <userID> — пригласить в группу\n");
            sb.append("\\group exclude <groupID> <userID> — исключить из группы\n");
            sb.append("\\group delete <groupID> — удалить группу\n");
            sb.append("\\group schedule set <groupID> <cron> — расписание группы\n");
            sb.append("\\group schedule off <groupID> — отключить расписание группы\n");
            sb.append("\\get questions all — все вопросы\n");
            sb.append("\\get questions <topic> — вопросы по теме\n");
            sb.append("\\update difficulty — адаптивное обновление сложности (LLM)\n");
        } else {
            sb.append("\nКоманды просмотра вопросов:\n");
            sb.append("\\get questions — список доступных тем\n");
        }
        return sb.toString();
    }

    private Mono<String> handleGetQuestions(Users user, String text) {
        String param = text.replace("\\get questions", "").trim();
        if (param.isEmpty()) {
            return topicService.getAllTopics()
                    .map(Topic::name)
                    .collectList()
                    .map(list -> "Доступные темы:\n" + String.join(", ", list));
        }

        if (user.role() != Role.ADMIN) {
            return Mono.just("Команда \\get questions <Topic> доступна только администраторам");
        }

        if (param.equalsIgnoreCase("all")) {
            return questionService.getAllQuestions()
                    .map(q -> String.format("[%s] %s (Тема: %s)", q.id(), q.text(), String.join(", ", q.topicNames())))
                    .collectList()
                    .map(list -> list.isEmpty() ? "Вопросов нет" : String.join("\n", list));
        }

        return questionService.getQuestionsByTopic(param)
                .map(q -> String.format("[%s] %s", q.id(), q.text()))
                .collectList()
                .map(list -> list.isEmpty() ? "Вопросов по теме " + param + " не найдено" : String.join("\n", list));
    }
}
