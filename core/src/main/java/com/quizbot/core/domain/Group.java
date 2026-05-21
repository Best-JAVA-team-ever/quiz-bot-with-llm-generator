package com.quizbot.core.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("groups")
public record Group(
        @Id String id,
        String name,
        String inviteLink,
        String scheduleCron,
        Instant createdAt,
        Instant deletedAt
) {
    public static Group create(String name, String inviteLink) {
        return new Group(null, name, inviteLink, null, Instant.now(), null);
    }

    public Group withName(String newName) {
        return new Group(id, newName, inviteLink, scheduleCron, createdAt, deletedAt);
    }

    public Group withInviteLink(String newLink) {
        return new Group(id, name, newLink, scheduleCron, createdAt, deletedAt);
    }

    public Group withScheduleCron(String newCron) {
        return new Group(id, name, inviteLink, newCron, createdAt, deletedAt);
    }

    public Group markAsDeleted() {
        return new Group(id, name, inviteLink, scheduleCron, createdAt, Instant.now());
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}