package com.quizbot.core.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

@Document(collection = "users")
public record User(
    @Id Long telegramId,
    UserRole role,
    LocalDateTime registrationDate
) {}
