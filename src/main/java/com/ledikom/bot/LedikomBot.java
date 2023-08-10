package com.ledikom.bot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.Timer;
import java.util.TimerTask;

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
    private Integer couponMessageId;


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
                sendCoupon(botService.getCoupon(), chatId);

                new Timer().schedule(
                        new TimerTask() {
                            @Override
                            public void run() {
                                deleteMessage(chatId, couponMessageId);
                            }
                        },
//                        5 * 60 * 1000 // 5 minutes in milliseconds
                        5000
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
            couponMessageId = sentMessage.getMessageId();
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

}
