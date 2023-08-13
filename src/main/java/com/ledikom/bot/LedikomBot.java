package com.ledikom.bot;

import com.ledikom.model.Coupon;
import com.ledikom.model.User;
import com.ledikom.model.UserCouponKey;
import com.ledikom.model.UserCouponRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
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
        } else if (update.hasCallbackQuery()) {
            var qry = update.getCallbackQuery();
            var chatId = qry.getMessage().getChatId();

            processMessage(qry.getData(), chatId);
        }

    }

    public void processMessage(String command, Long chatId) {

        switch (command) {
            case "/start" -> sendMessage(botService.addUserAndGenerateHelloMessage(chatId));

            case "Активировать приветственный купон" -> {
                Coupon coupon = botService.generateHelloCouponForUserIfNotUsed(chatId);

                if (coupon == null) {
                    sendMessage("Вы уже использовали свой приветственный купон.", chatId);
                } else {
                    // TODO: Add unique coupon to text
                    String couponTextWithUniqueSign = coupon.getText();
                    UserCouponKey userCouponKey = sendCoupon("Времени осталось: 5:00" +
                            "\n\n" +
                            couponTextWithUniqueSign, chatId);
                    botService.addCouponToMap(userCouponKey, couponTextWithUniqueSign);
                }
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

    private UserCouponKey sendCoupon(String couponText, Long chatId) {
        var sm = SendMessage.builder()
                .chatId(chatId)
                .text(couponText).build();
        try {
            Message sentMessage = execute(sm);
            return new UserCouponKey(sentMessage.getChatId(), sentMessage.getMessageId());
        } catch (Exception e) {
            log.trace(e.getMessage());
        }

        return null;
    }

    private void editMessage(final Long chatId, final Integer messageId, final String botReply) {
        var editMessageText = EditMessageText.builder()
                .chatId(chatId)
                .messageId(messageId)
                .text(botReply)
                .build();

        try {
            execute(editMessageText);
        } catch (Exception e) {
            log.trace(e.getMessage());
        }
    }

    private void updateCouponTimerAndMessage(final UserCouponKey userCouponKey, final UserCouponRecord userCouponRecord) {
        long timeLeftInSeconds = (userCouponRecord.getExpiryTimestamp() - System.currentTimeMillis()) / 1000;

        if (timeLeftInSeconds >= 0) {
            final String text = "Времени осталось: " + timeLeftInSeconds / 60 + ":" + (timeLeftInSeconds % 60 < 10 ? "0" + timeLeftInSeconds % 60 : timeLeftInSeconds % 60) +
                    "\n\n" +
                    userCouponRecord.getText();

            editMessage(userCouponKey.getChatId(), userCouponKey.getMessageId(), text);
        } else {
            editMessage(userCouponKey.getChatId(), userCouponKey.getMessageId(), "Время вашего купона истекло.");
        }

    }

    @Scheduled(fixedRate = 1000)
    public void processCouponsInMap() {
        BotService.userCoupons.entrySet().removeIf(userCoupon -> userCoupon.getValue().getExpiryTimestamp() < System.currentTimeMillis() - 5000);
        BotService.userCoupons.forEach(this::updateCouponTimerAndMessage);
    }
}
