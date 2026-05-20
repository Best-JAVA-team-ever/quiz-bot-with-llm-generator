package com.quizbot.api.telegram;

import com.quizbot.api.dispatcher.BotResponse;
import com.quizbot.api.dispatcher.MessageDispatcher;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
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
                .subscribe(response -> sendResponse(chatId, null, response));

        } else if (update.hasMessage()) {
            long chatId = update.getMessage().getChatId();
            sendResponse(chatId, null, BotResponse.text("Некорректный формат сообщения"));

        } else if (update.hasCallbackQuery()) {
            long chatId = update.getCallbackQuery().getMessage().getChatId();
            int messageId = update.getCallbackQuery().getMessage().getMessageId();
            String data = update.getCallbackQuery().getData();

            messageDispatcher.handleCommand(chatId, data)
                .subscribe(response -> sendResponse(chatId, messageId, response));
        }
    }

    public void sendResponse(long chatId, Integer messageId, BotResponse response) {
        if (response.editMode() && messageId != null) {
            EditMessageText edit = EditMessageText.builder()
                    .chatId(String.valueOf(chatId))
                    .messageId(messageId)
                    .text(response.text())
                    .replyMarkup(createKeyboardMarkup(response.keyboard()))
                    .build();
            try {
                telegramClient.execute(edit);
            } catch (TelegramApiException e) {
                // If editing fails (e.g. content same), just send a new message
                sendNewMessage(chatId, response);
            }
        } else {
            sendNewMessage(chatId, response);
        }
    }

    private void sendNewMessage(long chatId, BotResponse response) {
        SendMessage sendMessage = SendMessage.builder()
                .chatId(String.valueOf(chatId))
                .text(response.text())
                .replyMarkup(createKeyboardMarkup(response.keyboard()))
                .build();
        try {
            telegramClient.execute(sendMessage);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private InlineKeyboardMarkup createKeyboardMarkup(List<List<BotResponse.Button>> keyboard) {
        if (keyboard == null || keyboard.isEmpty()) return null;
        List<InlineKeyboardRow> rows = new ArrayList<>();
        for (List<BotResponse.Button> row : keyboard) {
            List<InlineKeyboardButton> buttons = new ArrayList<>();
            for (BotResponse.Button btn : row) {
                buttons.add(InlineKeyboardButton.builder()
                        .text(btn.text())
                        .callbackData(btn.callbackData())
                        .build());
            }
            rows.add(new InlineKeyboardRow(buttons));
        }
        return new InlineKeyboardMarkup(rows);
    }

    public void sendText(long chatId, String text) {
        sendNewMessage(chatId, BotResponse.text(text));
    }
}
