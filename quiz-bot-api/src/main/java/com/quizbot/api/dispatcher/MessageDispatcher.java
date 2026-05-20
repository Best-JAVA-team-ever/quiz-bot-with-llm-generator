package com.quizbot.api.dispatcher;

import com.quizbot.core.domain.Question;
import com.quizbot.core.domain.Users;
import com.quizbot.core.domain.Role;
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
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
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
    private final ConversationContextRepository contextRepository;

    public MessageDispatcher(UserService userService, QuestionService questionService, TopicService topicService, QuizService quizService, StatisticsService statisticsService, LlmClient llmClient, ScheduleService scheduleService, GroupService groupService, TelegramClient telegramClient, ConversationContextRepository contextRepository) {
        this.userService = userService;
        this.questionService = questionService;
        this.topicService = topicService;
        this.quizService = quizService;
        this.statisticsService = statisticsService;
        this.llmClient = llmClient;
        this.scheduleService = scheduleService;
        this.groupService = groupService;
        this.telegramClient = telegramClient;
        this.contextRepository = contextRepository;
    }

    public Mono<BotResponse> handleCommand(Long userId, String textIn) {
        String text = textIn.startsWith("/") ? "\\" + textIn.substring(1) : textIn;
        log.info("Received command from user {}: {}", userId, text);

        return contextRepository.findById(userId)
                .defaultIfEmpty(new ConversationContext(userId))
                .flatMap(context -> {
                    if (text.equalsIgnoreCase("\\cancel")) {
                        context.reset();
                        return contextRepository.save(context).thenReturn(BotResponse.text("Действие отменено"));
                    }

                    return userService.getOrCreateUser(userId).flatMap(user -> {
                        if (context.getState() == UserState.IN_QUIZ) {
                            if (text.startsWith("\\") && !text.startsWith("\\ans_") && !text.startsWith("\\quiz_")) {
                                context.reset();
                                return contextRepository.save(context).thenReturn(BotResponse.text("Викторина окончена"));
                            }
                            return processQuizAnswer(userId, context, text)
                                    .flatMap(resp -> contextRepository.save(context).thenReturn(resp));
                        }
                        if (context.getState() != UserState.IDLE) {
                            return handleConversation(user, context, text)
                                    .flatMap(resp -> contextRepository.save(context).thenReturn(resp));
                        }

                        boolean isAdmin = user.role() == Role.ADMIN;

                        Mono<BotResponse> actionMono;
                        if (text.startsWith("\\upgrade") && isAdmin) actionMono = handleUpgrade(text).map(BotResponse::text);
                        else if (text.startsWith("\\add tag") && isAdmin) actionMono = handleAddTag(text).map(BotResponse::text);
                        else if (text.startsWith("\\add question gen") && isAdmin) actionMono = startGenerateQuestion(context, text);
                        else if (text.startsWith("\\add question") && isAdmin) actionMono = startAddQuestion(context, text);
                        else if (text.startsWith("\\update question") && isAdmin) actionMono = startUpdateQuestion(context, text);
                        else if (text.startsWith("\\update difficulty") && isAdmin) actionMono = handleUpdateDifficulty().map(BotResponse::text);
                        else if (text.startsWith("\\delete question") && isAdmin) actionMono = handleDeleteQuestion(context, text);
                        else if (text.startsWith("\\update tag") && isAdmin) actionMono = handleUpdateTag(userId, text).map(BotResponse::text);
                        else if (text.startsWith("\\delete tag") && isAdmin) actionMono = handleDeleteTag(text).map(BotResponse::text);
                        else if (text.startsWith("\\schedule") && isAdmin) actionMono = handleSchedule(text).map(BotResponse::text);
                        else if (text.startsWith("\\group")) actionMono = handleGroup(user, context, text);
                        else if (text.startsWith("\\get questions")) actionMono = handleGetQuestions(user, text).map(BotResponse::text);
                        else if (text.startsWith("\\quiz start")) actionMono = startQuiz(userId, context, text);
                        else if (text.startsWith("\\score")) actionMono = handleScore(userId, context, text);
                        else if (text.equalsIgnoreCase("\\help")) actionMono = Mono.just(BotResponse.text(handleHelp(user)));
                        else actionMono = Mono.just(BotResponse.text("Неизвестная команда. Введите \\help для списка доступных команд."));

                        return actionMono.flatMap(resp -> contextRepository.save(context).thenReturn(resp));
                    });
                });
    }

    private Mono<BotResponse> startQuiz(Long userId, ConversationContext context, String text) {
        String params = text.replace("\\quiz start", "").trim();
        List<String> topics = params.isEmpty() ? List.of() : Arrays.asList(params.split(" "));

        return Flux.fromIterable(topics)
                .flatMap(t -> topicService.exists(t).map(exists -> new String[]{t, exists.toString()}))
                .collectList()
                .flatMap(results -> {
                    for (String[] pair : results) {
                        if (!"true".equals(pair[1]))
                            return Mono.just(BotResponse.text("Темы " + pair[0] + " не существует"));
                    }
                    context.setState(UserState.IN_QUIZ);
                    context.setPendingTopics(topics);
                    return nextQuizQuestion(userId, context);
                });
    }

    private Mono<BotResponse> nextQuizQuestion(Long userId, ConversationContext context) {
        return quizService.startQuiz(userId, context.getPendingTopics())
                .flatMap(q -> {
                    context.setActiveQuestion(q);
                    List<String> options = new ArrayList<>(q.wrongAnswers());
                    options.add(q.correctAnswer());
                    Collections.shuffle(options);

                    StringBuilder sb = new StringBuilder();
                    sb.append("Темы: ").append(String.join(", ", q.topicNames())).append("\n");
                    sb.append("Вопрос: ").append(q.text()).append("\n\n");
                    
                    List<List<BotResponse.Button>> keyboard = new ArrayList<>();
                    for (String opt : options) {
                        keyboard.add(List.of(new BotResponse.Button(opt, "\\ans_" + opt)));
                    }

                    return Mono.just(BotResponse.buttons(sb.toString(), keyboard));
                })
                .switchIfEmpty(Mono.defer(() -> {
                    context.reset();
                    return Mono.just(BotResponse.text("Нет неотвеченных вопросов"));
                }));
    }

    private Mono<BotResponse> processQuizAnswer(Long userId, ConversationContext context, String textIn) {
        String text = textIn.startsWith("\\ans_") ? textIn.substring(5) : textIn;
        Question q = context.getActiveQuestion();
        if (q == null) return Mono.just(BotResponse.text("Викторина не активна"));

        boolean isCorrect = q.correctAnswer().equalsIgnoreCase(text);

        if (textIn.equalsIgnoreCase("\\quiz_ok")) {
            return Mono.just(BotResponse.edit("Ответ некорректный", null))
                    .delayElement(java.time.Duration.ofMillis(500))
                    .then(nextQuizQuestion(userId, context));
        }
        if (textIn.equalsIgnoreCase("\\quiz_explain")) {
            String explanation = "Ответ некорректный\nКорректный ответ: " + q.correctAnswer() +
                    "\nПояснение: " + (q.explanation() != null ? q.explanation() : "нет");
            List<List<BotResponse.Button>> keyboard = List.of(List.of(new BotResponse.Button("Ок", "\\quiz_ok")));
            return Mono.just(BotResponse.edit(explanation, keyboard));
        }

        return quizService.recordAnswer(userId, q.id(), text, isCorrect).then(Mono.defer(() -> {
            if (isCorrect) {
                return nextQuizQuestion(userId, context).map(next -> {
                    return BotResponse.edit("Ответ корректный\n\n" + next.text(), next.keyboard());
                });
            } else {
                String msg = "Ответ некорректный\nКорректный ответ: " + q.correctAnswer() +
                        "\nПояснение: " + (q.explanation() != null ? q.explanation() : "нет");
                List<List<BotResponse.Button>> keyboard = List.of(
                        List.of(new BotResponse.Button("Ок", "\\quiz_ok"),
                                new BotResponse.Button("Объяснить", "\\quiz_explain"))
                );
                return Mono.just(BotResponse.edit(msg, keyboard));
            }
        }));
    }

    private Mono<BotResponse> performGeneration(ConversationContext context) {
        return llmClient.generateQuestion(context.getPendingTopics(), context.getDifficulty())
                .timeout(java.time.Duration.ofSeconds(20))
                .flatMap(q -> {
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
                                    return BotResponse.text(sb.toString());
                                });
                            });
                })
                .onErrorResume(e -> {
                    if (e instanceof java.util.concurrent.TimeoutException) {
                        context.setState(UserState.AWAITING_GENERATION_TIMEOUT_CONTINUE);
                        List<List<BotResponse.Button>> keyboard = List.of(List.of(
                                new BotResponse.Button("Да", "Да"),
                                new BotResponse.Button("Нет", "Нет")
                        ));
                        return Mono.just(BotResponse.buttons(
                                "Время генерации превысило допустимое значение. Продолжить генерацию?", keyboard));
                    }
                    String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                    return Mono.just(BotResponse.text("Генерация прервана по причине: " + errorMsg));
                });
    }

    private Mono<BotResponse> handleGroup(Users user, ConversationContext context, String text) {
        boolean isAdmin = user.role() == Role.ADMIN;
        String params = text.replace("\\group", "").trim();

        if (isAdmin) {
            if (params.startsWith("create")) {
                String name = params.replace("create", "").trim();
                if (name.isEmpty())
                    return Mono.just(BotResponse.text("Использование: \\group create <название>"));
                return groupService.createGroup(name).map(g -> BotResponse.text(String.format(
                        "Группа %s %s создана\nСсылка: %s", g.id(), g.name(), g.inviteLink())));
            }
            if (params.startsWith("invite")) {
                String[] parts = params.replace("invite", "").trim().split(" ");
                if (parts.length < 2) return Mono.just(BotResponse.text("Использование: \\group invite <ID группы> <ID пользователя>"));
                String groupId = parts[0];
                long invitedUserId;
                try {
                    invitedUserId = Long.parseLong(parts[1]);
                } catch (NumberFormatException e) {
                    return Mono.just(BotResponse.text("Некорректный ID пользователя"));
                }
                return groupService.getGroup(groupId)
                    .flatMap(g -> {
                        String msg = "Вас пригласили в группу «" + g.name() + "»";
                        List<List<BotResponse.Button>> keyboard = List.of(List.of(
                                new BotResponse.Button("Вступить", "\\group_join_" + groupId),
                                new BotResponse.Button("Отклонить", "\\group_decline_" + groupId)
                        ));
                        try {
                            telegramClient.execute(SendMessage.builder()
                                .chatId(String.valueOf(invitedUserId))
                                .text(msg)
                                .replyMarkup(InlineKeyboardMarkup.builder()
                                        .keyboardRow(new InlineKeyboardRow(List.of(
                                                InlineKeyboardButton.builder().text("Вступить").callbackData("\\group_join_" + groupId).build(),
                                                InlineKeyboardButton.builder().text("Отклонить").callbackData("\\group_decline_" + groupId).build()
                                        )))
                                        .build())
                                .build());
                        } catch (TelegramApiException e) {
                            log.warn("Не удалось уведомить пользователя {}: {}", invitedUserId, e.getMessage());
                        }
                        return Mono.just(BotResponse.text("Приглашение пользователю " + invitedUserId + " в группу «" + g.name() + "» отправлено"));
                    })
                    .switchIfEmpty(Mono.just(BotResponse.text("Группа не найдена")));
            }
            if (params.startsWith("exclude")) {
                String[] parts = params.replace("exclude", "").trim().split(" ");
                if (parts.length < 2)
                    return Mono.just(BotResponse.text("Использование: \\group exclude <ID группы> <ID пользователя>"));
                return groupService.removeUserFromGroup(parts[0], Long.parseLong(parts[1]))
                        .thenReturn(BotResponse.text("Пользователь удален из группы"));
            }
            if (params.startsWith("delete")) {
                String groupId = params.replace("delete", "").trim();
                if (groupId.isEmpty())
                    return Mono.just(BotResponse.text("Использование: \\group delete <ID группы>"));
                context.setDeleteScope("group");
                context.setDeleteValue(groupId);
                context.setState(UserState.AWAITING_GROUP_DELETE_CONFIRMATION);
                List<List<BotResponse.Button>> keyboard = List.of(List.of(
                        new BotResponse.Button("Да", "Да"),
                        new BotResponse.Button("Нет", "Нет")
                ));
                return Mono.just(BotResponse.buttons("Вы уверены?", keyboard));
            }
            if (params.startsWith("schedule set")) {
                String[] parts = params.replace("schedule set", "").trim().split(" ", 2);
                if (parts.length < 2)
                    return Mono.just(BotResponse.text("Использование: \\group schedule set <ID группы> <cron-выражение>"));
                try {
                    org.springframework.scheduling.support.CronExpression.parse(parts[1]);
                    return scheduleService.setGroupSchedule(parts[0], parts[1])
                            .thenReturn(BotResponse.text("Расписание для группы установлено: " + parts[1]));
                } catch (Exception e) {
                    return Mono.just(BotResponse.text("Некорректное cron-выражение"));
                }
            }
            if (params.startsWith("schedule off")) {
                String groupId = params.replace("schedule off", "").trim();
                if (groupId.isEmpty())
                    return Mono.just(BotResponse.text("Использование: \\group schedule off <ID группы>"));
                return scheduleService.disableGroupSchedule(groupId).thenReturn(BotResponse.text("Групповое расписание отключено"));
            }
            if (params.equalsIgnoreCase("list")) {
                return groupService.getAllGroups()
                        .flatMap(g -> groupService.findMembers(g.id())
                                .map(m -> "  • " + m.userId())
                                .collectList()
                                .map(members -> g.id() + " " + g.name() +
                                        (members.isEmpty() ? " (нет участников)" :
                                                "\n" + String.join("\n", members))))
                        .collectList()
                        .map(l -> BotResponse.text(l.isEmpty() ? "Групп нет." : String.join("\n\n", l)));
            }
            if (params.equalsIgnoreCase("score")) {
                return groupService.getAllGroups()
                        .flatMap(g -> statisticsService.getGroupStats(g.id()).map(stats -> String.format(
                                "Группа %s: всего %d, верных %d (%.1f%%)",
                                g.name(), stats.get("total"), stats.get("correct"), stats.get("percentage"))))
                        .collectList().map(l -> BotResponse.text(l.isEmpty() ? "Групп нет." : String.join("\n", l)));
            }
        }

        if (params.equalsIgnoreCase("leave")) {
            return groupService.getGroupsForUser(user.telegramId()).collectList().map(myGroups -> {
                if (myGroups.isEmpty())
                    return BotResponse.text("Вы не состоите в группах");
                List<List<BotResponse.Button>> keyboard = new ArrayList<>();
                for (var g : myGroups) {
                    keyboard.add(List.of(new BotResponse.Button(g.name(), "\\group_leave_" + g.id())));
                }
                return BotResponse.buttons("Выберите группу, которую хотите покинуть:", keyboard);
            });
        }
        if (params.equalsIgnoreCase("score")) {
            return groupService.getGroupsForUser(user.telegramId())
                    .flatMap(g -> statisticsService.getGroupStats(g.id()).map(stats -> String.format(
                            "Группа %s: всего %d, верных %d (%.1f%%)",
                            g.name(), stats.get("total"), stats.get("correct"), stats.get("percentage"))))
                    .collectList().map(l -> BotResponse.text(String.join("\n", l)));
        }

        if (text.startsWith("\\group_join_")) {
            String groupId = text.replace("\\group_join_", "").trim();
            return groupService.addUserToGroup(groupId, user.telegramId()).thenReturn(BotResponse.edit("Вы успешно вступили", null));
        }
        if (text.startsWith("\\group_decline_")) {
            return Mono.just(BotResponse.edit("Вы отказались от входа.", null));
        }
        if (text.startsWith("\\group_leave_")) {
            String groupId = text.replace("\\group_leave_", "").trim();
            return groupService.removeUserFromGroup(groupId, user.telegramId()).thenReturn(BotResponse.text("Вы вышли из группы"));
        }

        if (text.startsWith("\\start join_")) {
            String inviteId = text.replace("\\start join_", "").trim();
            return groupService.getGroup(inviteId)
                    .flatMap(g -> groupService.addUserToGroup(g.id(), user.telegramId())
                            .thenReturn(BotResponse.text("Вы успешно вступили в группу «" + g.name() + "»!")))
                    .switchIfEmpty(Mono.just(BotResponse.text("Приглашение недействительно")));
        }

        return Mono.just(BotResponse.text("Неизвестная подкоманда \\group"));
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
                            s.isActive() ? "активно" : "отключено",
                            s.cronExpression(),
                            s.topicName() != null ? s.topicName() : "не задана"))
                    .switchIfEmpty(Mono.just("Расписание не задано"));
        }
        return Mono.just("Использование: \\schedule <set cron|off|status>");
    }

    private Mono<BotResponse> handleScore(Long userId, ConversationContext context, String text) {
        String param = text.replace("\\score", "").trim();

        if (param.equalsIgnoreCase("reset")) {
            context.setState(UserState.AWAITING_SCORE_RESET_CONFIRMATION);
            List<List<BotResponse.Button>> keyboard = List.of(List.of(
                    new BotResponse.Button("Да", "Да"),
                    new BotResponse.Button("Нет", "Нет")
            ));
            return Mono.just(BotResponse.buttons("Вы уверены? Весь прогресс будет удалён", keyboard));
        }

        String topicName = param.isEmpty() ? null : param;
        if (topicName != null) {
            return topicService.exists(topicName).flatMap(exists -> {
                if (!exists)
                    return Mono.just(BotResponse.text("Темы " + topicName + " не существует"));
                return fetchStats(userId, topicName).map(BotResponse::text);
            });
        }
        return fetchStats(userId, null).map(BotResponse::text);
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

    private Mono<BotResponse> startAddQuestion(ConversationContext context, String text) {
        String params = text.replace("\\add question", "").trim();
        if (params.isEmpty())
            return Mono.just(BotResponse.text("Прервано добавление вопроса по причине: Не указаны темы"));
        List<String> topics = Arrays.stream(params.split(" ")).filter(s -> !s.isBlank()).collect(Collectors.toList());

        return Flux.fromIterable(topics)
                .flatMap(t -> topicService.exists(t).flatMap(exists -> {
                    if (exists) return Mono.empty();
                    return topicService.addTopic(t).map(newTopic -> "Добавлена новая тема " + t);
                }))
                .collectList()
                .flatMap(newTopicMessages -> {
                    String prefix = String.join("\n", newTopicMessages);
                    if (!prefix.isEmpty()) prefix += "\n";
                    context.setPendingTopics(topics);
                    context.setState(UserState.AWAITING_QUESTION_TEXT);
                    return Mono.just(BotResponse.text(prefix + "Введите текст вопроса:"));
                })
                .onErrorResume(e -> {
                    context.reset();
                    return Mono.just(BotResponse.text("Ошибка при создании темы: " + e.getMessage()));
                });
    }

    private Mono<BotResponse> startUpdateQuestion(ConversationContext context, String text) {
        String id = text.replace("\\update question", "").trim();
        if (id.isEmpty())
            return Mono.just(BotResponse.text("Использование: \\update question <ID>"));
        return questionService.getQuestionById(id).map(q -> {
            context.setPendingQuestion(q);
            context.setUpdateFieldIndex(0);
            context.setState(UserState.AWAITING_UPDATE_FIELD_CHOICE);
            List<List<BotResponse.Button>> keyboard = List.of(List.of(
                    new BotResponse.Button("Изменить", "Изменить"),
                    new BotResponse.Button("Оставить без изменений", "Оставить без изменений")
            ));
            return BotResponse.buttons("Текущий текст: " + q.text() + "\nЖелаете изменить?", keyboard);
        }).switchIfEmpty(Mono.just(BotResponse.text("Вопрос с таким ID не существует")));
    }

    private Mono<String> handleUpdateTag(Long userId, String text) {
        String params = text.replace("\\update tag", "").trim();
        String[] parts = params.split(" ", 2);
        if (parts.length < 2 || parts[0].isBlank() || parts[1].isBlank()) {
            return Mono.just("Использование: \\update tag <ID> <новое_название>");
        }
        String topicId = parts[0].trim();
        String newName = parts[1].trim();
        return topicService.rename(String.valueOf(userId), topicId, newName)
                .map(t -> "Тема с ID \"" + topicId + "\" успешно обновлена. Новое название: \"" + newName + "\"")
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

    private Mono<BotResponse> handleDeleteQuestion(ConversationContext context, String text) {
        String param = text.replace("\\delete question", "").trim();
        if (param.isEmpty())
            return Mono.just(BotResponse.text("Использование: \\delete question <ID|Тема|all>"));

        List<List<BotResponse.Button>> keyboard = List.of(List.of(
                new BotResponse.Button("Да", "Да"),
                new BotResponse.Button("Нет", "Нет")
        ));

        if (param.equalsIgnoreCase("all")) {
            context.setDeleteScope("all");
            context.setState(UserState.AWAITING_CONFIRMATION);
            return Mono.just(BotResponse.buttons("Вы уверены?", keyboard));
        } else {
            return questionService.getQuestionById(param)
                .flatMap(q -> {
                    context.setDeleteScope("id");
                    context.setDeleteValue(param);
                    context.setState(UserState.AWAITING_CONFIRMATION);
                    return Mono.just(BotResponse.buttons("Вы уверены?", keyboard));
                })
                .onErrorResume(e -> topicService.exists(param).flatMap(exists -> {
                    if (exists) {
                        context.setDeleteScope("topic");
                        context.setDeleteValue(param);
                        context.setState(UserState.AWAITING_CONFIRMATION);
                        return Mono.just(BotResponse.buttons("Вы уверены?", keyboard));
                    }
                    return Mono.just(BotResponse.text("Вопрос с таким ID или тема не найдены"));
                }));
        }
    }

    private Mono<BotResponse> startGenerateQuestion(ConversationContext context, String text) {
        String params = text.replace("\\add question gen", "").trim();
        if (params.isEmpty())
            return Mono.just(BotResponse.text("Не было введено название темы"));
        List<String> topics = new ArrayList<>(Arrays.asList(params.split(" ")));
        for (String t : topics) {
            if (!topicService.isValid(t))
                return Mono.just(BotResponse.text(t + " — некорректное название темы"));
        }
        context.setPendingTopics(topics);
        context.setState(UserState.AWAITING_GENERATION_DIFFICULTY);
        List<List<BotResponse.Button>> keyboard = List.of(List.of(
                new BotResponse.Button("1", "1"),
                new BotResponse.Button("2", "2"),
                new BotResponse.Button("3", "3"),
                new BotResponse.Button("4", "4"),
                new BotResponse.Button("5", "5")
        ));
        return Mono.just(BotResponse.buttons("Выберите уровень сложности", keyboard));
    }

    private Mono<BotResponse> handleConversation(Users user, ConversationContext context, String text) {
        switch (context.getState()) {
            case AWAITING_GENERATION_DIFFICULTY:
                try {
                    int diff = Integer.parseInt(text);
                    if (diff < 1 || diff > 5)
                        throw new Exception();
                    context.setDifficulty(diff);
                    String genNotice = "Генерация вопроса по теме(ам) " +
                            String.join(", ", context.getPendingTopics()) +
                            " с уровнем сложности " + diff;
                    try {
                        telegramClient.execute(SendMessage.builder()
                                .chatId(String.valueOf(user.telegramId()))
                                .text(genNotice)
                                .build());
                    } catch (TelegramApiException ex) {
                        log.warn("Не удалось отправить уведомление о генерации: {}", ex.getMessage());
                    }
                    return performGeneration(context);
                } catch (Exception e) {
                    List<List<BotResponse.Button>> keyboard = List.of(List.of(
                            new BotResponse.Button("1", "1"),
                            new BotResponse.Button("2", "2"),
                            new BotResponse.Button("3", "3"),
                            new BotResponse.Button("4", "4"),
                            new BotResponse.Button("5", "5")
                    ));
                    return Mono.just(BotResponse.buttons("Выберите уровень сложности от 1 до 5", keyboard));
                }
            case AWAITING_GENERATION_TIMEOUT_CONTINUE:
                if (text.equalsIgnoreCase("Да"))
                    return performGeneration(context);
                context.reset();
                return Mono.just(BotResponse.text("Генерация прервана"));
            case AWAITING_GROUP_DELETE_CONFIRMATION:
                if (text.equalsIgnoreCase("Да")) {
                    String groupId = context.getDeleteValue();
                    return groupService.deleteGroup(groupId).then(Mono.defer(() -> {
                        context.reset();
                        return Mono.just(BotResponse.edit("Группа удалена", null));
                    }));
                } else {
                    context.reset();
                    return Mono.just(BotResponse.edit("Удаление отменено", null));
                }
            case AWAITING_SCORE_RESET_CONFIRMATION:
                if (text.equalsIgnoreCase("Да")) {
                    return statisticsService.resetUserStats(user.telegramId()).then(Mono.defer(() -> {
                        context.reset();
                        return Mono.just(BotResponse.edit("Счёт сброшен", null));
                    }));
                } else {
                    context.reset();
                    return Mono.just(BotResponse.edit("Сброс отменён", null));
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
                        return Mono.just(BotResponse.edit("Вопросы удалены", null));
                    }));
                } else {
                    context.reset();
                    return Mono.just(BotResponse.edit("Удаление отменено", null));
                }
            case AWAITING_UPDATE_FIELD_CHOICE:
                if (text.equalsIgnoreCase("Изменить")) {
                    context.setState(UserState.AWAITING_UPDATE_NEW_VALUE);
                    return Mono.just(BotResponse.text("Введите новое значение:"));
                } else if (text.equalsIgnoreCase("Оставить без изменений")) {
                    return nextUpdateField(context);
                } else {
                    List<List<BotResponse.Button>> keyboard = List.of(List.of(
                            new BotResponse.Button("Изменить", "Изменить"),
                            new BotResponse.Button("Оставить без изменений", "Оставить без изменений")
                    ));
                    return Mono.just(BotResponse.buttons("Используйте кнопки", keyboard));
                }
            case AWAITING_UPDATE_NEW_VALUE:
                return applyUpdateValue(context, text);
            case AWAITING_QUESTION_TEXT:
                if (!text.matches("^[а-яА-Яa-zA-Z0-9\\s\\.,!?;:\\-\"\\'()]{4,128}$")) {
                    context.reset();
                    return Mono.just(BotResponse.text("Прервано добавление вопроса по причине: некорректный текст вопроса"));
                }
                context.setQuestionText(text);
                context.setState(UserState.AWAITING_CORRECT_ANSWER);
                return Mono.just(BotResponse.text("Введите текст правильного ответа:"));
            case AWAITING_CORRECT_ANSWER:
                if (!isValidAnswer(text)) {
                    context.reset();
                    return Mono.just(BotResponse.text("Прервано добавление вопроса по причине: некорректный текст ответа"));
                }
                context.setCorrectAnswer(text);
                context.setState(UserState.AWAITING_INCORRECT_ANSWER_1);
                return Mono.just(BotResponse.text("Введите текст неправильного ответа 1:"));
            case AWAITING_INCORRECT_ANSWER_1:
            case AWAITING_INCORRECT_ANSWER_2:
                if (!isValidAnswer(text)) {
                    context.reset();
                    return Mono.just(BotResponse.text("Прервано добавление вопроса по причине: некорректный текст ответа"));
                }
                context.getIncorrectAnswers().add(text);
                context.setState(context.getState() == UserState.AWAITING_INCORRECT_ANSWER_1
                        ? UserState.AWAITING_INCORRECT_ANSWER_2
                        : UserState.AWAITING_INCORRECT_ANSWER_3);
                return Mono.just(BotResponse.text("Введите текст неправильного ответа " +
                        (context.getState() == UserState.AWAITING_INCORRECT_ANSWER_2 ? "2:" : "3:")));
            case AWAITING_INCORRECT_ANSWER_3:
                if (!isValidAnswer(text)) {
                    context.reset();
                    return Mono.just(BotResponse.text("Прервано добавление вопроса по причине: некорректный текст ответа"));
                }
                context.getIncorrectAnswers().add(text);
                context.setState(UserState.AWAITING_DIFFICULTY);
                List<List<BotResponse.Button>> keyboardDiff = List.of(List.of(
                        new BotResponse.Button("1", "1"),
                        new BotResponse.Button("2", "2"),
                        new BotResponse.Button("3", "3"),
                        new BotResponse.Button("4", "4"),
                        new BotResponse.Button("5", "5")
                ));
                return Mono.just(BotResponse.buttons("Выберите уровень сложности вопроса (1-5):", keyboardDiff));
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
                                    return BotResponse.text(sb.toString());
                                });
                            }).onErrorResume(ex -> {
                                Question q = Question.create(
                                        qText, correctAns, incorrectAnswers,
                                        difficulty, "Пояснение недоступно (ошибка LLM)", null,
                                        pendingTopics);
                                return questionService.addQuestion(q).map(saved -> {
                                    context.reset();
                                    return BotResponse.text("Вопрос успешно добавлен под идентификатором " + saved.id()
                                            + "\nСгенерированное пояснение: Пояснение недоступно (ошибка LLM)");
                                });
                            });
                } catch (Exception e) {
                    context.reset();
                    return Mono.just(BotResponse.text("Прервано добавление вопроса по причине: некорректный уровень сложности"));
                }
            default:
                context.reset();
                return Mono.just(BotResponse.text("Ошибка состояния. Процесс прерван."));
        }
    }

    private Mono<BotResponse> nextUpdateField(ConversationContext context) {
        int index = context.getUpdateFieldIndex() + 1;
        context.setUpdateFieldIndex(index);
        Question q = context.getPendingQuestion();

        List<List<BotResponse.Button>> keyboard = List.of(List.of(
                new BotResponse.Button("Изменить", "Изменить"),
                new BotResponse.Button("Оставить без изменений", "Оставить без изменений")
        ));

        if (index == 1)
            return Mono.just(BotResponse.edit("Текущий правильный ответ: " + q.correctAnswer()
                    + "\nЖелаете изменить?", keyboard));
        if (index == 2)
            return Mono.just(BotResponse.edit("Текущие неправильные ответы: " + String.join(", ", q.wrongAnswers())
                    + "\nЖелаете изменить?", keyboard));
        if (index == 3)
            return Mono.just(BotResponse.edit("Текущая сложность: " + q.difficulty()
                    + "\nЖелаете изменить?", keyboard));
        if (index == 4)
            return Mono.just(BotResponse.edit("Текущее пояснение: " + (q.explanation() != null ? q.explanation() : "нет")
                    + "\nЖелаете изменить?", keyboard));

        return questionService.updateQuestion(context.getPendingQuestion()).map(saved -> {
            String id = saved.id();
            context.reset();
            return BotResponse.edit("Вопрос " + id + " успешно обновлён", null);
        });
    }

    private Mono<BotResponse> applyUpdateValue(ConversationContext context, String text) {
        Question q = context.getPendingQuestion();
        int index = context.getUpdateFieldIndex();

        try {
            if (index == 0) {
                if (!text.matches("^[а-яА-Яa-zA-Z0-9\\s\\.,!?;:\\-\"\\'()]{4,128}$"))
                    throw new Exception("некорректный текст");
                context.setPendingQuestion(q.withText(text));
            } else if (index == 1) {
                if (!isValidAnswer(text))
                    throw new Exception("некорректный ответ");
                context.setPendingQuestion(q.withCorrectAnswer(text));
            } else if (index == 2) {
                List<String> list = Arrays.stream(text.split(",")).map(String::trim).collect(Collectors.toList());
                if (list.size() != 3)
                    throw new Exception("введите 3 ответа через запятую");
                context.setPendingQuestion(q.withWrongAnswers(list));
            } else if (index == 3) {
                int diff = Integer.parseInt(text);
                if (diff < 1 || diff > 5)
                    throw new Exception("1-5");
                context.setPendingQuestion(q.withDifficulty(diff));
            } else if (index == 4) {
                context.setPendingQuestion(q.withExplanation(text));
            }
        } catch (Exception e) {
            return Mono.just(BotResponse.text("Ошибка: " + e.getMessage() + ". Попробуйте еще раз или \\cancel:"));
        }

        context.setState(UserState.AWAITING_UPDATE_FIELD_CHOICE);
        return nextUpdateField(context);
    }

    private boolean isValidAnswer(String text) {
        return text.matches("^[а-яА-Яa-zA-Z0-9\\s\\.,!?;:\\-\"\\'()]{2,32}$");
    }

    private Mono<String> handleGetQuestions(Users user, String text) {
        boolean isAdmin = user.role() == Role.ADMIN;
        String params = text.replace("\\get questions", "").trim();

        if (isAdmin) {
            if (params.isEmpty()) {
                return topicService.getAllTopics()
                        .flatMap(t -> questionService.getQuestionsByTopic(t.name()).count()
                                .map(c -> t.name() + ": " + c))
                        .collectList()
                        .map(list -> list.isEmpty() ? "В системе пока нет тем." : String.join("\n", list));
            } else if (params.equalsIgnoreCase("all")) {
                return questionService.getAllQuestions().collectList().map(this::formatQuestionList);
            } else {
                return questionService.getQuestionsByTopic(params).collectList()
                        .map(qs -> qs.isEmpty() ? "Вопросов по теме " + params + " не найдено."
                                : formatQuestionList(qs));
            }
        } else {
            if (!params.isEmpty())
                return Mono.just("Недоступная команда");
            return topicService.getAllTopics()
                    .flatMap(t -> questionService.getQuestionsByTopic(t.name()).count()
                            .flatMap(total -> statisticsService.getUserStats(user.telegramId(), t.name())
                                    .map(stats -> String.format("%s: всего %d, пройдено %d", t.name(), total,
                                            stats.get("correct")))))
                    .collectList().map(list -> "Темы и ваш прогресс:\n" + String.join("\n", list));
        }
    }

    private String formatQuestionList(List<Question> questions) {
        if (questions.isEmpty())
            return "Список пуст.";
        return questions.stream()
                .map(q -> String.format("ID: %s | Сложность: %d | Тема: %s\nQ: %s\nПравильный: %s\nНеправильные: %s",
                        q.id(), q.difficulty(), String.join(", ", q.topicNames()),
                        q.text(), q.correctAnswer(), String.join(", ", q.wrongAnswers())))
                .collect(Collectors.joining("\n---\n"));
    }

    private String handleHelp(Users user) {
        if (user.role() == Role.ADMIN) {
            return "Команды администратора:\n\\add tag <название>\n\\add question <тема1>...\n\\add question gen <тема1>...\n\\update question <ID>\n\\delete question <ID|Тема|all>\n\\get questions [all|<тема>]\n\\upgrade <ID>\n\\update difficulty\n\\update tag <ID> <новое_название>\n\\cancel\n\\group create <название>\n\\group invite <ID_группы> <ID_пользователя>\n\\group exclude <ID_группы> <ID_пользователя>\n\\group delete <ID_группы>\n\\group list\n\\group score\n\\group schedule set <ID_группы> <cron>\n\\group schedule off <ID_группы>\n\\schedule set <cron>\n\\schedule off\n\\schedule status\n\nПользовательские команды:\n\\quiz start [тема]\n\\score [тема|reset]\n\\group leave\n\\group score\n\\help";
        } else {
            return "Пользовательские команды:\n\\quiz start [тема]\n\\score [тема|reset]\n\\group leave\n\\group score\n\\help";
        }
    }
}