package com.ledikom.bot;

import com.ledikom.model.Coupon;
import com.ledikom.model.User;
import com.ledikom.model.UserCouponKey;
import com.ledikom.model.UserCouponRecord;
import com.ledikom.utils.BotResponses;
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

@Component
public class LedikomBot extends TelegramLongPollingBot {

    @Value("${bot_username}")
    private String botUsername;
    @Value("${bot_token}")
    private String botToken;
    @Value("${tech_admin_id}")
    private Long techAdminId;
    @Value("${coupon.time-in-minutes}")
    private int couponTimeInMinutes;
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

//    kupony - Мои активные купоны
//    moya_ssylka - Моя реферальная ссылка
    public void processMessage(String command, Long chatId) {

        if(command.startsWith("couponPreview_")) {
            generateCouponAcceptMessageIfNotUsed(command, chatId);
            return;
        }
        if (command.startsWith("couponAccept_")) {
            generateCouponIfNotUsed(command, chatId);
            return;
        }
        if (command.startsWith("/start")) {
            botService.processRefLink(command, chatId);
            sendMessage(botService.addUserAndGenerateHelloMessage(chatId));
            return;
        }

        switch (command) {
            case "/kupony" -> sendMessage(botService.showAllCoupons(chatId));

            case "/moya_ssylka" -> sendMessage(botService.getReferralLinkForUser(chatId));

            case "/setnotification" -> System.out.println("setnotification");

            case "/deletenotification" -> System.out.println("deletenotification");

            case "/showpharmacies" -> System.out.println("pharmacies");

            default -> {
                log.error("Illegal State", new IllegalStateException());
                sendMessage(new IllegalStateException().toString(), techAdminId);
            }
        }
    }

    private void generateCouponIfNotUsed(final String couponCommand, final Long chatId) {
        Coupon coupon = botService.generateCouponIfNotUsed(couponCommand, chatId);

        if (coupon == null) {
            sendMessage(BotResponses.couponUsedMessage(), chatId);
        } else {
            String couponTextWithUniqueSign = botService.generateSignedCoupon(coupon);
            UserCouponKey userCouponKey = sendCoupon("Времени осталось: " + couponTimeInMinutes + ":00" +
                    "\n\n" +
                    couponTextWithUniqueSign, chatId);
            botService.addCouponToMap(userCouponKey, couponTextWithUniqueSign);
        }
    }

    private void generateCouponAcceptMessageIfNotUsed(final String couponCommand, final long chatId) {
        sendMessage(botService.generateCouponAcceptMessageIfNotUsed(couponCommand, chatId));
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
            if (sm != null) {
                execute(sm);
            }
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

    private void editMessage(final Long chatId, final Integer messageId, final String editedMessage) {
        var editMessageText = EditMessageText.builder()
                .chatId(chatId)
                .messageId(messageId)
                .text(editedMessage)
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
            editMessage(userCouponKey.getChatId(), userCouponKey.getMessageId(), botService.generateCouponText(userCouponRecord, timeLeftInSeconds));
        } else {
            editMessage(userCouponKey.getChatId(), userCouponKey.getMessageId(), "Время вашего купона истекло.");
        }

    }

    @Scheduled(fixedRate = 1000)
    public void processCouponsInMap() {
        BotService.userCoupons.forEach(this::updateCouponTimerAndMessage);
        botService.removeExpiredCouponsFromMap();
    }
}
