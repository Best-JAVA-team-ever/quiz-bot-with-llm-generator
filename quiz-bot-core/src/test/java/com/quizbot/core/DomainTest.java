package com.quizbot.core;

import com.quizbot.core.domain.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DomainTest {

    @Test
    void question_create_idIsNullAndTimestampsAreEqual() {
        Question q = Question.create("text", "correct", List.of("wrong"), 2, "exp", "hint", List.of("java"));
        assertNull(q.id());
        assertEquals(q.createdAt(), q.updatedAt());
        assertNull(q.deletedAt());
    }

    @Test
    void question_withText_doesNotMutateOriginal() {
        Question q = Question.create("old", "a", List.of(), 1, "", "", List.of());
        assertEquals("new", q.withText("new").text());
        assertEquals("old", q.text());
    }

    @Test
    void question_withText_updatesTimestamp() {
        Question q = Question.create("old", "a", List.of(), 1, "", "", List.of());
        Question updated = q.withText("new");
        assertFalse(updated.updatedAt().isBefore(q.createdAt()));
    }

    @Test
    void question_markAsDeleted_setsDeletedAt() {
        Question q = Question.create("t", "a", List.of(), 1, "", "", List.of());
        assertFalse(q.isDeleted());
        assertTrue(q.markAsDeleted().isDeleted());
    }


    @Test
    void answers_create_idIsNullAndTimestampIsSetAutomatically() {
        Answers a = Answers.create("user1", "q1", "A", true);
        assertNull(a.id());
        assertNotNull(a.answeredAt());
    }

    @Test
    void users_create_isActiveTrueByDefault() {
        Users u = Users.create(42L, Role.USER);
        assertTrue(u.isActive());
        assertNull(u.deletedAt());
    }

    @Test
    void users_withRole_changesRoleButPreservesOtherFields() {
        Users u = Users.create(42L, Role.USER);
        Users admin = u.withRole(Role.ADMIN);
        assertEquals(Role.ADMIN, admin.role());
        assertEquals(42L, admin.telegramId());
        assertEquals(u.registeredAt(), admin.registeredAt());
    }

    @Test
    void users_markAsDeleted_setsDeletedAtAndDeactivates() {
        Users u = Users.create(1L, Role.USER);
        assertFalse(u.isDeleted());
        Users deleted = u.markAsDeleted();
        assertTrue(deleted.isDeleted());
        assertFalse(deleted.isActive());
    }


    @Test
    void group_create_scheduleIsNullByDefault() {
        Group g = Group.create("MyGroup", "link");
        assertNull(g.scheduleCron());
        assertNull(g.id());
        assertNull(g.deletedAt());
    }

    @Test
    void group_withName_preservesInviteLink() {
        Group g = Group.create("OldName", "https://t.me/quiz_bot?start=join_abc");
        Group renamed = g.withName("NewName");
        assertEquals("NewName", renamed.name());
        assertEquals("https://t.me/quiz_bot?start=join_abc", renamed.inviteLink());
    }

    @Test
    void group_markAsDeleted_setsDeletedAt() {
        Group g = Group.create("G", "link");
        assertFalse(g.isDeleted());
        assertTrue(g.markAsDeleted().isDeleted());
    }


    @Test
    void topic_markAsDeleted_setsDeletedAt() {
        Topic t = Topic.create("java");
        assertNull(t.id());
        assertFalse(t.isDeleted());
        assertTrue(t.markAsDeleted().isDeleted());
    }

    @Test
    void topic_withName_doesNotMutateOriginal() {
        Topic t = Topic.create("old");
        assertEquals("new", t.withName("new").name());
        assertEquals("old", t.name());
    }
}
