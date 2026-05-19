package com.quizbot.core.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("users")
public record Users(
    @Id String id,
    @Indexed(unique = true) Long telegramId,
    Role role,
    boolean isActive, 
    Instant registeredAt, 
    Instant scoreResetAt, 
    Instant deletedAt
) {
    // Фабричный метод для нового пользователя
    public static Users create(Long telegramId, Role role) {
        return new Users(null, telegramId, role, true, Instant.now(), Instant.now(), null);
    }

    public Users withRole(Role newRole) {
        return new Users(id, telegramId, newRole, isActive, registeredAt, scoreResetAt, deletedAt);
    }

    public Users withScoreResetAt(Instant resetAt) {
        return new Users(id, telegramId, role, isActive, registeredAt, resetAt, deletedAt);
    }

    public Users withIsActive(boolean active) {
        return new Users(id, telegramId, role, active, registeredAt, scoreResetAt, deletedAt);
    }

    public Users markAsDeleted() {
        return new Users(id, telegramId, role, false, registeredAt, scoreResetAt, Instant.now());
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
