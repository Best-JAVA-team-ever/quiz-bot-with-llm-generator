package com.quizbot.core.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "schedules")
public record QuizSchedule(
    @Id String id, // Could be 'global' or 'group:{groupId}'
    String cronExpression,
    boolean isActive,
    String topicName // optional
) {}
