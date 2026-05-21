package com.quizbot.bot;

import com.quizbot.bot.dispatcher.BotResponse;
import com.quizbot.bot.dispatcher.ConversationContext;
import com.quizbot.bot.dispatcher.UserState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ApiTest {

    @Test
    void botResponse_text_hasNoKeyboardAndNoEditMode() {
        BotResponse r = BotResponse.text("hello");
        assertNull(r.keyboard());
        assertFalse(r.editMode());
    }

    @Test
    void botResponse_edit_setsEditMode() {
        BotResponse text = BotResponse.text("msg");
        BotResponse edit = BotResponse.edit("msg", null);
        assertFalse(text.editMode());
        assertTrue(edit.editMode());
    }

    @Test
    void botResponse_buttons_hasKeyboardButNoEditMode() {
        var keyboard = List.of(List.of(new BotResponse.Button("Yes", "yes")));
        BotResponse r = BotResponse.buttons("Choose:", keyboard);
        assertNotNull(r.keyboard());
        assertFalse(r.editMode());
    }


    @Test
    void conversationContext_defaultState_isIdle() {
        ConversationContext ctx = new ConversationContext(1L);
        assertEquals(UserState.IDLE, ctx.getState());
        assertNull(ctx.getActiveQuestion());
        assertTrue(ctx.getPendingTopics().isEmpty());
        assertTrue(ctx.getIncorrectAnswers().isEmpty());
    }

    @Test
    void conversationContext_reset_clearsAllFields() {
        ConversationContext ctx = new ConversationContext(1L);
        ctx.setState(UserState.IN_QUIZ);
        ctx.setQuestionText("Что такое JVM?");
        ctx.setCorrectAnswer("Виртуальная машина");
        ctx.setDifficulty(3);
        ctx.getPendingTopics().add("java");

        ctx.reset();

        assertEquals(UserState.IDLE, ctx.getState());
        assertNull(ctx.getQuestionText());
        assertNull(ctx.getCorrectAnswer());
        assertNull(ctx.getDifficulty());
        assertTrue(ctx.getPendingTopics().isEmpty());
    }
}
