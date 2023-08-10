package com.ledikom.bot;

import com.ledikom.utils.BotResponses;
import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Setter
@Getter
public class BotService {

    static final Map<Long, LocalDateTime> usersCouponTime= new HashMap<>();

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

    String getCoupon() {
        LocalDateTime startTime = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

        return """
                %s
                
                LEDIKOM BOT 2023
                """.formatted(startTime.format(formatter));
    }

}
