package com.ledikom.bot;

import com.ledikom.user.User;
import com.ledikom.utils.BotResponses;
import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Setter
@Getter
public class BotService {

    static final Map<Long, User> users = new HashMap<>();

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

    String getCoupon(Long chatId) {
        var startTime = LocalTime.now();

        users.put(chatId, new User(chatId, startTime, true));

        return """
                Времени осталось: %s
                
                LEDIKOM BOT 2023
                """.formatted(formatTime(getTimeLeft(startTime)));
    }

    public Duration getTimeLeft(LocalTime startTime) {
        LocalTime currentTime = LocalTime.now();
        Duration elapsed = Duration.between(startTime, currentTime);
        Duration duration = Duration.ofMinutes(1);

        return duration.minus(elapsed);
    }

    public String formatTime(Duration time) {
        long MM = time.toMinutesPart();
        long SS = time.toSecondsPart();

        return String.format("%02d:%02d", MM, SS);
    }

}
