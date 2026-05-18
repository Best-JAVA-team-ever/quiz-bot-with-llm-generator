package com.quizbot.api.telegram;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Component
public class TelegramBotLauncher {

    private final String botToken;
    private final QuizTelegramBot quizTelegramBot;
    private TelegramBotsLongPollingApplication botsApplication;

    public TelegramBotLauncher(@Value("${telegram.bot.token}") String botToken,
                               QuizTelegramBot quizTelegramBot) {
        this.botToken = botToken;
        this.quizTelegramBot = quizTelegramBot;
    }

    @PostConstruct
    public void start() {
        try {
            botsApplication = new TelegramBotsLongPollingApplication();
            botsApplication.registerBot(botToken, quizTelegramBot);
            System.out.println("Telegram Bot started using Long Polling...");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @PreDestroy
    public void stop() {
        if (botsApplication != null) {
            try {
                botsApplication.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
