package com.ledikom.bot;

import com.ledikom.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import static com.ledikom.bot.BotService.users;

@Component
public class LedikomBot extends TelegramLongPollingBot {

    @Value("${bot_username}")
    private String botUsername;
    @Value("${bot_token}")
    private String botToken;
    @Value("${tech_admin_id}")
    private Long techAdminId;
    private final BotService botService;
    private final Logger log = LoggerFactory.getLogger(LedikomBot.class);

    public LedikomBot(BotService botService) {
        this.botService = botService;
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public String getBotToken() {
        return botToken;
    }

    @Override
    public void onUpdateReceived(Update update) {

        if (update.hasMessage() && update.getMessage().hasText()) {
            var msg = update.getMessage();
            var chatId = msg.getChatId();

            processMessage(msg.getText(), chatId);
        }
        else if (update.hasCallbackQuery()) {
            var qry = update.getCallbackQuery();
            var chatId = qry.getMessage().getChatId();

            processMessage(qry.getData(), chatId);
        }

    }

    public void processMessage(String command, Long chatId) {

        switch (command) {
            case "/start" -> sendMessage(botService.start(chatId));

            case "Активировать купон" -> {

//                if (users.get(chatId).isCouponUsed()) {
//                    sendMessage("Вы уже использовали свой купон.", chatId);
//                }

                sendCoupon(botService.getCoupon(chatId), chatId);

                new Timer().schedule(
                        new TimerTask() {
                            @Override
                            public void run() {
                                deleteMessage(chatId, users.get(chatId).getCouponMessageId());
                                users.remove(chatId);
                            }
                        },
                        5 * 60 * 1000 // 5 minutes in milliseconds
//                        5000
                );
            }

            case "/setnotification" -> System.out.println("setnotification");

            case "/deletenotification" -> System.out.println("deletenotification");

            case "/showpharmacies" -> System.out.println("pharmacies");

            default -> {
                log.error("Illegal State", new IllegalStateException());
                sendMessage(new IllegalStateException().toString(), techAdminId);
            }
        }

    }

    private void sendMessage(String botReply, Long chatId) {
        var sm = SendMessage.builder()
                .chatId(chatId)
                .text(botReply).build();

        try {
            execute(sm);
        } catch (Exception e) {
            log.trace(e.getMessage());
        }
    }

    private void sendMessage(SendMessage sm) {
        try {
            execute(sm);
        } catch (Exception e) {
            log.trace(e.getMessage());
        }
    }

    private void sendCoupon(String botReply, Long chatId) {
        var sm = SendMessage.builder()
                .chatId(chatId)
                .text(botReply).build();

        try {
            Message sentMessage = execute(sm);
            users.get(chatId).setCouponMessageId(sentMessage.getMessageId());
        } catch (Exception e) {
            log.trace(e.getMessage());
        }
    }

    private void deleteMessage(Long chatId, int messageId) {
        var dm = new DeleteMessage(String.valueOf(chatId), messageId);

        try {
            execute(dm);
        } catch (Exception e) {
            log.trace(e.getMessage());
        }
    }


    @Scheduled(fixedRate = 1000)
    void editMessage() {
        if (users.isEmpty()) {return;}

        for (Map.Entry<Long, User> entry : users.entrySet()) {
            var chatId = entry.getKey();
            var user = entry.getValue();

            var editMessageText = EditMessageText.builder()
                    .chatId(chatId)
                    .messageId(user.getCouponMessageId())
                    .text("""
                            Времени осталось: %s
                                            
                            LEDIKOM BOT 2023
                            """.formatted(botService.countTime(user.getCouponStartTime()))
                    ).build();

            try {
                execute(editMessageText);
            } catch (Exception e) {
                log.trace(e.getMessage());
            }

        }

    }
}
