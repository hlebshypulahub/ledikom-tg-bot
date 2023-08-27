package com.ledikom.service;

import com.ledikom.bot.LedikomBot;
import com.ledikom.callback.GetFileFromBotCallback;
import com.ledikom.callback.SendMessageCallback;
import com.ledikom.callback.SendMessageWithPhotoCallback;
import com.ledikom.model.RequestFromAdmin;
import com.ledikom.model.NewsFromAdmin;
import com.ledikom.model.User;
import com.ledikom.utils.AdminMessageToken;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.polls.Poll;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
public class AdminService {

    public static final String DELIMITER = "&";

    private final BotUtilityService botUtilityService;
    private final PollService pollService;
    private final UserService userService;
    private final LedikomBot ledikomBot;
    private final CouponService couponService;

    private SendMessageCallback sendMessageCallback;
    private SendMessageWithPhotoCallback sendMessageWithPhotoCallback;

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
        this.sendMessageWithPhotoCallback = ledikomBot.getSendMessageWithPhotoCallback();
    }

    public List<String> getSplitStrings(final String messageFromAdmin) {
        if (messageFromAdmin == null || messageFromAdmin.isBlank()) {
            return List.of(AdminMessageToken.NEWS.label);
        }
        List<String> splitStringsFromAdminMessage = new ArrayList<>(Arrays.stream(messageFromAdmin.split(DELIMITER)).map(String::trim).toList());
        splitStringsFromAdminMessage.set(0, splitStringsFromAdminMessage.get(0).toLowerCase());
        return splitStringsFromAdminMessage;
    }

    public NewsFromAdmin getNewsByAdmin(final List<String> splitStringsFromAdminMessage, final String photoPath) {
        return new NewsFromAdmin(splitStringsFromAdminMessage.size() > 1 ? splitStringsFromAdminMessage.get(1) : "", photoPath);
    }

    public RequestFromAdmin getRequestFromAdmin(final Update update, final GetFileFromBotCallback getFileFromBotCallback) {
        RequestFromAdmin requestFromAdmin = new RequestFromAdmin();

        var msg = update.getMessage();
        String photoPath;
        if (msg.hasPhoto() || msg.hasDocument()) {
            photoPath = botUtilityService.getPhotoFromUpdate(msg, getFileFromBotCallback);
            requestFromAdmin.setPhotoPath(photoPath);
            requestFromAdmin.setMessage(msg.getCaption());
        } else if (msg.hasText()) {
            requestFromAdmin.setMessage(msg.getText());
        } else if (msg.hasPoll()) {
            requestFromAdmin.setPoll(msg.getPoll());
        }

        return requestFromAdmin;
    }

    public void executeAdminActionOnPollReceived(final Poll poll) {
        com.ledikom.model.Poll entityPoll = pollService.tgPollToLedikomPoll(poll);
        entityPoll.setLastVoteTimestamp(LocalDateTime.now());
        pollService.savePoll(entityPoll);
        sendPollToUsers(poll);
    }

    public void executeAdminActionOnMessageReceived(final RequestFromAdmin requestFromAdmin) throws IOException {
        List<String> splitStringsFromAdminMessage = getSplitStrings(requestFromAdmin.getMessage());

        if (splitStringsFromAdminMessage.get(0).equals(AdminMessageToken.NEWS.label)) {
            sendNewsToUsers(requestFromAdmin.getPhotoPath(), splitStringsFromAdminMessage);
        } else if (splitStringsFromAdminMessage.get(0).equals(AdminMessageToken.COUPON.label)) {
            couponService.createAndSendNewCoupon(requestFromAdmin.getPhotoPath(), splitStringsFromAdminMessage);
        }
    }

    private void sendPollToUsers(final Poll poll) {
        List<User> usersToSendNews = userService.getAllUsersToReceiveNews();
        usersToSendNews.forEach(user -> sendMessageCallback.execute(botUtilityService.buildSendPoll(poll, user.getChatId())));
    }

    private void sendNewsToUsers(final String photoPath, final List<String> splitStringsFromAdminMessage) throws IOException {
        NewsFromAdmin newsFromAdmin = getNewsByAdmin(splitStringsFromAdminMessage, photoPath);
        List<User> usersToSendNews = userService.getAllUsersToReceiveNews();

        if (newsFromAdmin.getPhotoPath() == null || newsFromAdmin.getPhotoPath().isBlank()) {
            usersToSendNews.forEach(user -> sendMessageCallback.execute(botUtilityService.buildSendMessage(newsFromAdmin.getNews(), user.getChatId())));
        } else {
            InputStream imageStream = new URL(photoPath).openStream();
            InputFile inputFile = new InputFile(imageStream, "image.jpg");
            usersToSendNews.forEach(user -> sendMessageWithPhotoCallback.execute(inputFile, newsFromAdmin.getNews(), user.getChatId()));
        }
    }
}
