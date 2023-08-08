package com.ledikom.bot;

import com.ledikom.utils.BotResponses;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;

@Component
public class BotService {
    SendMessage start(Long chatId) {
        var sm = SendMessage.builder()
                .chatId(chatId)
                .text(BotResponses.startMessage()).build();

        var markup = new InlineKeyboardMarkup();

        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        var button = new InlineKeyboardButton();
        button.setText("Активировать купон");
        button.setCallbackData("Активировать купон");
        List<InlineKeyboardButton> row = new ArrayList<>();
        row.add(button);
        keyboard.add(row);

        markup.setKeyboard(keyboard);

        sm.setReplyMarkup(markup);

        return sm;
    }
}
