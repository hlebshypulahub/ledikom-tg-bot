package com.ledikom.service;

import com.ledikom.bot.LedikomBot;
import com.ledikom.callback.GetFileFromBotCallback;
import com.ledikom.callback.SendMessageCallback;
import com.ledikom.model.NewsFromAdmin;
import com.ledikom.utils.AdminMessageToken;
import com.ledikom.utils.BotCommands;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.polls.Poll;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

@Service
public class AdminService {

    public static final String DELIMITER = "&";

    @Value("${admin.id}")
    private Long adminId;
    @Value("${hello-coupon.barcode}")
    private String helloCouponBarcode;

    private final BotUtilityService botUtilityService;
    private final PollService pollService;
    private final UserService userService;
    private final LedikomBot ledikomBot;
    private final CouponService couponService;

    private SendMessageCallback sendMessageCallback;
    private GetFileFromBotCallback getFileFromBotCallback;

    public AdminService(final BotUtilityService botUtilityService, final PollService pollService, final UserService userService, final LedikomBot ledikomBot, final CouponService couponService) {
        this.botUtilityService = botUtilityService;
        this.pollService = pollService;
        this.userService = userService;
        this.ledikomBot = ledikomBot;
        this.couponService = couponService;
    }

    @PostConstruct
    public void initCallbacks() {
        this.sendMessageCallback = ledikomBot.getSendMessageCallback();
        this.getFileFromBotCallback = ledikomBot.getGetFileFromBotCallback();
    }

    public void processAdminRequest(final Update update) throws IOException {
        if (update.hasMessage() && update.getMessage().hasPoll()) {
            executeAdminActionOnPollReceived(update.getMessage().getPoll());
        } else if (update.hasMessage()) {
            executeAdminActionOnMessageReceived(update.getMessage());
        }
    }

    public void executeAdminActionOnPollReceived(final Poll poll) {
        com.ledikom.model.Poll entityPoll = pollService.tgPollToLedikomPoll(poll);
        entityPoll.setLastVoteTimestamp(LocalDateTime.now());
        pollService.savePoll(entityPoll);
        userService.sendPollToUsers(poll);
    }

    public void executeAdminActionOnMessageReceived(final Message message) throws IOException {
        String photoPath = null;
        if (botUtilityService.messageHasPhoto(message)) {
            photoPath = botUtilityService.getPhotoFromUpdate(message, getFileFromBotCallback);
        }

        String text = getTextFromAdminMessage(message);

        List<String> splitStringsFromAdminMessage = getSplitStringsFromAdminMessage(text);

        if (splitStringsFromAdminMessage.get(0).equalsIgnoreCase(AdminMessageToken.NEWS.label)) {
            if (adminCommandIsValid(AdminMessageToken.NEWS, splitStringsFromAdminMessage.size())) {
                userService.sendNewsToUsers(getNewsByAdmin(splitStringsFromAdminMessage, photoPath));
            }
        } else if (splitStringsFromAdminMessage.get(0).equalsIgnoreCase(AdminMessageToken.COUPON.label)) {
            if (adminCommandIsValid(AdminMessageToken.COUPON, splitStringsFromAdminMessage.size())) {
                couponService.createAndSendNewCoupon(photoPath, splitStringsFromAdminMessage);
            }
        }
    }

    private boolean adminCommandIsValid(final AdminMessageToken adminMessageToken, final int splitStringsSize) {
        if (splitStringsSize == adminMessageToken.commandSize) {
            return true;
        } else {
            sendMessageCallback.execute(botUtilityService.buildSendMessage("Неверный формат команды! Количество аргументов не равно " + adminMessageToken.commandSize, adminId));
            throw new RuntimeException("Неверный формат команды! Количество аргументов не равно " + AdminMessageToken.COUPON.commandSize + ", команда вызова: " + adminMessageToken.label);
        }
    }

    private String getTextFromAdminMessage(final Message message) {
        if (botUtilityService.messageHasPhoto(message) && !message.getCaption().isBlank()) {
            return message.getCaption();
        } else if (message.hasText() && !message.getText().isBlank()) {
            return message.getText();
        } else {
            sendMessageCallback.execute(botUtilityService.buildSendMessage("Неверный формат команды! Сообщение не может быть пустым!", adminId));
            throw new RuntimeException("Неверный формат команды! Сообщение не может быть пустым!");
        }
    }

    private List<String> getSplitStringsFromAdminMessage(final String messageFromAdmin) {
        List<String> splitStringsFromAdminMessage = new ArrayList<>(Arrays.stream(messageFromAdmin.split(DELIMITER)).map(String::trim).toList());

        if (splitStringsFromAdminMessage.size() == 1 && Stream.of(BotCommands.values()).noneMatch(botCommand -> botCommand.label.equals(splitStringsFromAdminMessage.get(0)))) {
            sendMessageCallback.execute(botUtilityService.buildSendMessage("Неверный формат команды! Не обнаруженно разделителя: " + DELIMITER, adminId));
            throw new RuntimeException("Неверный формат команды! Не обнаруженно разделителя: " + DELIMITER);
        }

        return splitStringsFromAdminMessage;
    }

    private NewsFromAdmin getNewsByAdmin(final List<String> splitStringsFromAdminMessage, final String photoPath) {
        return new NewsFromAdmin(splitStringsFromAdminMessage.get(1), photoPath);
    }
}
