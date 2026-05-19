package com.quizbot.core.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("audit_log")
@CompoundIndexes({
    @CompoundIndex(name = "target_idx", def = "{'targetCollection': 1, 'targetId': 1}")
})
public record AuditLog(
        @Id String id,
        @Indexed String initiatorId,
        String action,
        @Indexed String targetCollection,
        @Indexed String targetId,
        @Indexed Instant createdAt
) {
    public static AuditLog create(String initiatorId, String action,
                                   String targetCollection, String targetId) {
        return new AuditLog(null, initiatorId, action, targetCollection, targetId, Instant.now());
    }
}