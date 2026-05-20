package com.quizbot.api.telegram;

import com.quizbot.api.dispatcher.MessageDispatcher;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.ArrayList;
import java.util.List;

@Component
public class QuizTelegramBot implements LongPollingSingleThreadUpdateConsumer {

    private final MessageDispatcher messageDispatcher;
    private final TelegramClient telegramClient;

    public QuizTelegramBot(TelegramClient telegramClient, MessageDispatcher messageDispatcher) {
        this.messageDispatcher = messageDispatcher;
        this.telegramClient = telegramClient;
    }

    @Override
    public void consume(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            Message message = update.getMessage();
            long chatId = message.getChatId();
            String text = message.getText();

            messageDispatcher.handleCommand(chatId, text)
                .subscribe(response -> sendText(chatId, response));

        } else if (update.hasMessage()) {
            long chatId = update.getMessage().getChatId();
            sendText(chatId, "Некорректный формат сообщения");

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

    public void sendQuestionWithOptions(long chatId, String text, List<String> options) {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        for (int i = 0; i < options.size(); i++) {
            rows.add(new InlineKeyboardRow(List.of(
                    InlineKeyboardButton.builder()
                            .text(options.get(i))
                            .callbackData("\\ans_" + i)
                            .build()
            )));
        }
        rows.add(new InlineKeyboardRow(List.of(
                InlineKeyboardButton.builder()
                        .text("Закончить викторину")
                        .callbackData("\\cancel")
                        .build()
        )));
        SendMessage sendMessage = SendMessage.builder()
                .chatId(String.valueOf(chatId))
                .text(text)
                .replyMarkup(new InlineKeyboardMarkup(rows))
                .build();
        try {
            telegramClient.execute(sendMessage);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
}
