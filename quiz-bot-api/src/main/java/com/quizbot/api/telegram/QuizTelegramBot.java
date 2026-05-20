package com.quizbot.api.telegram;

import com.quizbot.api.dispatcher.MessageDispatcher;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

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
        if (update.hasMessage()) {
            Message message = update.getMessage();
            long chatId = message.getChatId();

            if (message.hasText()) {
                String text = message.getText();
                messageDispatcher.handleCommand(chatId, text)
                    .subscribe(response -> sendText(chatId, response));
            } else {
                sendText(chatId, "Некорректный формат сообщения");
            }
                
        } else if (update.hasCallbackQuery()) {
            long chatId = update.getCallbackQuery().getMessage().getChatId();
            String data = update.getCallbackQuery().getData();
            
            messageDispatcher.handleCommand(chatId, data)
                .subscribe(response -> sendText(chatId, response));
        }
    }

    public void sendText(long chatId, String text) {
        SendMessage.SendMessageBuilder<?, ?> builder = SendMessage.builder()
                .chatId(String.valueOf(chatId))
                .text(text);

        // Parse buttons in format [Label]
        java.util.List<java.util.List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton>> keyboard = new java.util.ArrayList<>();
        String[] lines = text.split("\n");
        for (String line : lines) {
            java.util.List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton> row = new java.util.ArrayList<>();
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\[(.*?)\\]");
            java.util.regex.Matcher matcher = pattern.matcher(line);
            while (matcher.find()) {
                String label = matcher.group(1);
                row.add(org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton.builder()
                        .text(label)
                        .callbackData(label)
                        .build());
            }
            if (!row.isEmpty()) {
                keyboard.add(row);
            }
        }

        if (!keyboard.isEmpty()) {
            java.util.List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow> rows = keyboard.stream()
                    .map(org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow::new)
                    .collect(java.util.stream.Collectors.toList());
            builder.replyMarkup(org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup.builder()
                    .keyboard(rows)
                    .build());
        }

        try {
            telegramClient.execute(builder.build());
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
}
