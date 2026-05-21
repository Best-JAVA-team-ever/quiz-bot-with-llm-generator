package com.quizbot.core;

import com.quizbot.core.domain.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DomainTest {


    @Test
    void question_create_setsFields() {
        Question q = Question.create("text", "correct", List.of("wrong"), 2, "exp", "hint", List.of("java"));
        assertNull(q.id());
        assertEquals("text", q.text());
        assertEquals("correct", q.correctAnswer());
        assertEquals(2, q.difficulty());
        assertEquals(List.of("java"), q.topicNames());
        assertNull(q.deletedAt());
    }

    @Test
    void question_withText_doesNotMutateOriginal() {
        Question q = Question.create("old", "a", List.of(), 1, "", "", List.of());
        assertEquals("new", q.withText("new").text());
        assertEquals("old", q.text());
    }

    @Test
    void question_markAsDeleted_setsDeletedAt() {
        Question q = Question.create("t", "a", List.of(), 1, "", "", List.of());
        assertFalse(q.isDeleted());
        assertTrue(q.markAsDeleted().isDeleted());
    }


    @Test
    void answers_create_setsFields() {
        Answers a = Answers.create("user1", "q1", "A", true);
        assertNull(a.id());
        assertEquals("user1", a.userId());
        assertEquals("q1", a.questionId());
        assertTrue(a.isCorrect());
        assertNotNull(a.answeredAt());
    }


    @Test
    void users_create_setsFields() {
        Users u = Users.create(42L, Role.USER);
        assertNull(u.id());
        assertEquals(42L, u.telegramId());
        assertEquals(Role.USER, u.role());
        assertTrue(u.isActive());
        assertNull(u.deletedAt());
    }

    @Test
    void users_withRole_changesRole() {
        Users u = Users.create(1L, Role.USER);
        assertEquals(Role.ADMIN, u.withRole(Role.ADMIN).role());
        assertEquals(Role.USER, u.role());
    }

    @Test
    void users_markAsDeleted_setsDeletedAt() {
        Users u = Users.create(1L, Role.USER);
        assertFalse(u.isDeleted());
        assertTrue(u.markAsDeleted().isDeleted());
    }


    @Test
    void group_create_setsFields() {
        Group g = Group.create("MyGroup", "https://t.me/quiz_bot?start=join_abc");
        assertNull(g.id());
        assertEquals("MyGroup", g.name());
        assertNull(g.scheduleCron());
        assertNull(g.deletedAt());
    }

    @Test
    void group_markAsDeleted_setsDeletedAt() {
        Group g = Group.create("G", "link");
        assertFalse(g.isDeleted());
        assertTrue(g.markAsDeleted().isDeleted());
    }


    @Test
    void topic_create_setsFields() {
        Topic t = Topic.create("java");
        assertNull(t.id());
        assertEquals("java", t.name());
        assertNull(t.deletedAt());
    }

    @Test
    void topic_markAsDeleted_setsDeletedAt() {
        Topic t = Topic.create("java");
        assertFalse(t.isDeleted());
        assertTrue(t.markAsDeleted().isDeleted());
    }
}
