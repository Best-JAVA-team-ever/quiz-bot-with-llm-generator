package com.quizbot.api.telegram;

import com.quizbot.api.dispatcher.MessageDispatcher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

@Component
public class QuizTelegramBot implements LongPollingSingleThreadUpdateConsumer {

    private final MessageDispatcher messageDispatcher;
    private final TelegramClient telegramClient;

    public QuizTelegramBot(@Value("${telegram.bot.token}") String botToken,
                           MessageDispatcher messageDispatcher) {
        this.messageDispatcher = messageDispatcher;
        this.telegramClient = new OkHttpTelegramClient(botToken);
    }

    @Override
    public void consume(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            Message message = update.getMessage();
            long chatId = message.getChatId();
            String text = message.getText();

            messageDispatcher.handleCommand(chatId, text)
                .subscribe(response -> sendText(chatId, response));
                
        } else if (update.hasCallbackQuery()) {
            long chatId = update.getCallbackQuery().getMessage().getChatId();
            String data = update.getCallbackQuery().getData();
            
            messageDispatcher.handleCommand(chatId, data)
                .subscribe(response -> sendText(chatId, response));
        }
    }

    public void sendText(long chatId, String text) {
        SendMessage sendMessage = SendMessage.builder()
                .chatId(String.valueOf(chatId))
                .text(text)
                .build();
        try {
            telegramClient.execute(sendMessage);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
}
