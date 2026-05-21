package com.quizbot.api;

import com.quizbot.api.dispatcher.BotResponse;
import com.quizbot.api.dispatcher.ConversationContext;
import com.quizbot.api.dispatcher.UserState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ApiTest {


    @Test
    void botResponse_text_setsTextAndNoKeyboard() {
        BotResponse r = BotResponse.text("hello");
        assertEquals("hello", r.text());
        assertNull(r.keyboard());
        assertFalse(r.editMode());
    }

    @Test
    void botResponse_buttons_setsKeyboard() {
        var keyboard = List.of(List.of(new BotResponse.Button("Yes", "yes")));
        BotResponse r = BotResponse.buttons("Choose:", keyboard);
        assertEquals("Choose:", r.text());
        assertEquals(keyboard, r.keyboard());
        assertFalse(r.editMode());
    }

    @Test
    void botResponse_edit_setsEditMode() {
        BotResponse r = BotResponse.edit("updated", null);
        assertTrue(r.editMode());
    }


    @Test
    void conversationContext_defaultState_isIdle() {
        ConversationContext ctx = new ConversationContext(1L);
        assertEquals(1L, ctx.getUserId());
        assertEquals(UserState.IDLE, ctx.getState());
        assertNull(ctx.getActiveQuestion());
    }

    @Test
    void conversationContext_setState_changesState() {
        ConversationContext ctx = new ConversationContext(1L);
        ctx.setState(UserState.IN_QUIZ);
        assertEquals(UserState.IN_QUIZ, ctx.getState());
    }

    @Test
    void conversationContext_reset_returnsToIdle() {
        ConversationContext ctx = new ConversationContext(1L);
        ctx.setState(UserState.IN_QUIZ);
        ctx.setQuestionText("some text");
        ctx.reset();
        assertEquals(UserState.IDLE, ctx.getState());
        assertNull(ctx.getQuestionText());
    }
}
