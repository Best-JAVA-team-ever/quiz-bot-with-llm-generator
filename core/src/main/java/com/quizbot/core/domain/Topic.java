package com.quizbot.core.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("topic")
public record Topic(
        @Id String id,
        @Indexed(unique = true) String name,
        Instant createdAt,
        Instant deletedAt
) {
    public static Topic create(String name) {
        return new Topic(null, name, Instant.now(), null);
    }

    public Topic withName(String newName) {
        return new Topic(id, newName, createdAt, deletedAt);
    }

    public Topic markAsDeleted() {
        return new Topic(id, name, createdAt, Instant.now());
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}