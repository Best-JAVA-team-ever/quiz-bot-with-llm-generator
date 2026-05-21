package com.quizbot.core.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("group_members")
@CompoundIndexes({
    @CompoundIndex(name = "group_user_idx", def = "{'groupId': 1, 'userId': 1}", unique = false)
})
public record GroupMember(
        @Id String id,
        @Indexed String groupId,
        @Indexed String userId,
        Instant joinedAt,
        Instant deletedAt
) {
    public static GroupMember create(String groupId, String userId) {
        return new GroupMember(null, groupId, userId, Instant.now(), null);
    }

    public GroupMember markAsDeleted() {
        return new GroupMember(id, groupId, userId, joinedAt, Instant.now());
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}