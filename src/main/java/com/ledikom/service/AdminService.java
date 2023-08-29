package com.ledikom.service;

import com.ledikom.bot.LedikomBot;
import com.ledikom.callback.GetFileFromBotCallback;
import com.ledikom.callback.SendMessageCallback;
import com.ledikom.model.NewsFromAdmin;
import com.ledikom.utils.AdminMessageToken;
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
    public void initCallbacks() throws IOException {
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
        String text = null;

        if (message.hasPhoto() || message.hasDocument()) {
            photoPath = botUtilityService.getPhotoFromUpdate(message, getFileFromBotCallback);
            text = message.getCaption();
        }
        if (message.hasText()) {
            text = message.getText();
        }

        List<String> splitStringsFromAdminMessage = getSplitStrings(text);

        if (splitStringsFromAdminMessage.get(0).equalsIgnoreCase(AdminMessageToken.NEWS.label)) {
            if (splitStringsFromAdminMessage.size() != AdminMessageToken.NEWS.commandSize) {
                sendMessageCallback.execute(botUtilityService.buildSendMessage("Неверный формат новости! Количество аргументов не равно " + AdminMessageToken.NEWS.commandSize, adminId));
                throw new RuntimeException("Неверный формат новости! Количество аргументов не равно " + AdminMessageToken.NEWS.commandSize);
            }
            userService.sendNewsToUsers(getNewsByAdmin(splitStringsFromAdminMessage, photoPath));
        } else if (splitStringsFromAdminMessage.get(0).equalsIgnoreCase(AdminMessageToken.COUPON.label)) {
            if (splitStringsFromAdminMessage.size() != AdminMessageToken.COUPON.commandSize) {
                sendMessageCallback.execute(botUtilityService.buildSendMessage("Неверный формат новости! Количество аргументов не равно " + AdminMessageToken.COUPON.commandSize, adminId));
                throw new RuntimeException("Неверный формат новости! Количество аргументов не равно " + AdminMessageToken.COUPON.commandSize);
            }
            couponService.createAndSendNewCoupon(photoPath, splitStringsFromAdminMessage);
        }
    }

    private List<String> getSplitStrings(final String messageFromAdmin) {
        if (messageFromAdmin == null || messageFromAdmin.isBlank()) {
            sendMessageCallback.execute(botUtilityService.buildSendMessage("Неверный формат команды! Сообщение не может быть пустым!", adminId));
            throw new RuntimeException("Неверный формат команды! Сообщение не может быть пустым!");
        }

        List<String> splitStringsFromAdminMessage = new ArrayList<>(Arrays.stream(messageFromAdmin.split(DELIMITER)).map(String::trim).toList());

        if (splitStringsFromAdminMessage.isEmpty()) {
            sendMessageCallback.execute(botUtilityService.buildSendMessage("Неверный формат команды! Не обнаруженно разделителя: " + DELIMITER, adminId));
            throw new RuntimeException("Неверный формат команды! Не обнаруженно разделителя: " + DELIMITER);
        }

        return splitStringsFromAdminMessage;
    }

    private NewsFromAdmin getNewsByAdmin(final List<String> splitStringsFromAdminMessage, final String photoPath) {
        return new NewsFromAdmin(splitStringsFromAdminMessage.get(1), photoPath);
    }
}
