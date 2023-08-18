package com.ledikom.bot;

import com.ledikom.callback.*;
import com.ledikom.model.UserCouponKey;
import com.ledikom.model.UserCouponRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.*;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Objects;

@Component
public class LedikomBot extends TelegramLongPollingBot {

    @Value("${bot.username}")
    private String botUsername;
    @Value("${bot.token}")
    private String botToken;
    @Value("${admin.tech-id}")
    private Long techAdminId;
    @Value("${coupon.duration-in-minutes}")
    private int couponDurationInMinutes;
    @Value("${admin.id}")
    private Long adminId;

    private final BotService botService;
    private String photoPathReceivedFromAdmin;

    private final SendMessageWithPhotoCallback sendMessageWithPhotoCallback;
    private final GetFileFromBotCallback getFileFromBotCallback;
    private final SendCouponCallback sendCouponCallback;
    private final SendMessageCallback sendMessageCallback;

    private final Logger log = LoggerFactory.getLogger(LedikomBot.class);

    public LedikomBot(BotService botService) {
        this.botService = botService;
        this.sendMessageWithPhotoCallback = this::sendImageWithCaption;
        this.getFileFromBotCallback = this::getFileFromBot;
        this.sendCouponCallback = this::sendCoupon;
        this.sendMessageCallback = this::sendMessage;
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

        if (update.hasMessage()) {
            var msg = update.getMessage();
            var chatId = msg.getChatId();
            if (Objects.equals(chatId, adminId)) {
                processAdminMessage(update);
            }
            if (msg.hasText()) {
                processMessage(msg.getText(), chatId);
            }
        } else if (update.hasCallbackQuery()) {
            var qry = update.getCallbackQuery();
            var chatId = qry.getMessage().getChatId();
            processMessage(qry.getData(), chatId);
        }
    }

    private void processAdminMessage(final Update update) {
        var msg = update.getMessage();
        String text = "";
        if (msg.hasPhoto()) {
            String photoPath = botService.getPhotoFromUpdate(msg, getFileFromBotCallback);
            if (photoPath != null) {
                photoPathReceivedFromAdmin = photoPath;
                text = msg.getCaption();
            }
        } else if (msg.hasText()) {
            text = msg.getText();
        }

        botService.processAdminMessage(sendMessageCallback, sendMessageWithPhotoCallback, text, photoPathReceivedFromAdmin);

        // reset
        photoPathReceivedFromAdmin = null;
    }

    private File getFileFromBot(final GetFile getFileRequest) throws TelegramApiException {
        return execute(getFileRequest);
    }

    //    kupony - Мои активные купоны
    //    moya_ssylka - Моя реферальная ссылка
    //    vkl_otkl_rassylku - Вкл/Откл рассылку новостей
    public void processMessage(String command, Long chatId) {

        if (command.startsWith("couponPreview_")) {
            botService.generateCouponAcceptMessageIfNotUsed(sendMessageCallback, command, chatId);
            return;
        }
        if (command.startsWith("couponAccept_")) {
            botService.generateCouponIfNotUsed(sendMessageCallback, sendCouponCallback, command, chatId);
            return;
        }
        if (command.startsWith("/start")) {
            botService.processRefLink(command, chatId);
            botService.addUserAndGenerateHelloMessage(sendMessageCallback, chatId);
            return;
        }

        switch (command) {
            case "/kupony" -> botService.showAllCoupons(sendMessageCallback, chatId);

            case "/moya_ssylka" -> botService.getReferralLinkForUser(sendMessageCallback, chatId);

            case "/vkl_otkl_rassylku" -> botService.generateTriggerReceiveNewsMessage(sendMessageCallback, chatId);

            case "/setnotification" -> System.out.println("setnotification");

            case "/deletenotification" -> System.out.println("deletenotification");

            case "/showpharmacies" -> System.out.println("pharmacies");
        }
    }

    private void sendImageWithCaption(String imageUrl, String caption, Long chatId) {
        try {
            InputStream imageStream = new URL(imageUrl).openStream();
            InputFile inputFile = new InputFile(imageStream, "image.jpg");
            SendPhoto sendPhoto = new SendPhoto();
            sendPhoto.setChatId(chatId.toString());
            sendPhoto.setPhoto(inputFile);
            sendPhoto.setCaption(caption);
            execute(sendPhoto);
        } catch (IOException | TelegramApiException e) {
            e.printStackTrace();
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

    private UserCouponKey sendCoupon(SendMessage sm) {
        try {
            Message sentMessage = execute(sm);
            return new UserCouponKey(sentMessage.getChatId(), sentMessage.getMessageId());
        } catch (Exception e) {
            log.trace(e.getMessage());
            return null;
        }
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
            editMessage(userCouponKey.getChatId(), userCouponKey.getMessageId(), botService.updateCouponText(userCouponRecord, timeLeftInSeconds));
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
