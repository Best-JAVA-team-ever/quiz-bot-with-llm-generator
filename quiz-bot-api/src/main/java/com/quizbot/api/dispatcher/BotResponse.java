package com.quizbot.api.dispatcher;

import java.util.List;

public record BotResponse(
    String text,
    List<List<Button>> keyboard,
    boolean editMode
) {
    public static BotResponse text(String text) {
        return new BotResponse(text, null, false);
    }

    public static BotResponse buttons(String text, List<List<Button>> keyboard) {
        return new BotResponse(text, keyboard, false);
    }

    public static BotResponse edit(String text, List<List<Button>> keyboard) {
        return new BotResponse(text, keyboard, true);
    }

    public record Button(String text, String callbackData) {}
}
