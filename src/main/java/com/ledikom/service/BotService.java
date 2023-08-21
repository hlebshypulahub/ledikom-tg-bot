package com.ledikom.service;

import com.ledikom.bot.LedikomBot;
import com.ledikom.callback.*;
import com.ledikom.model.*;
import com.ledikom.utils.AdminMessageToken;
import com.ledikom.utils.BotResponses;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.polls.Poll;

import java.time.LocalDateTime;
import java.util.*;

@Component
public class BotService {

    @Value("${bot.username}")
    private String botUsername;
    @Value("${hello-coupon.name}")
    private String helloCouponName;
    @Value("${coupon.duration-in-minutes}")
    private int couponDurationInMinutes;
    @Value("${admin.id}")
    private Long adminId;

    private final UserService userService;
    private final CouponService couponService;
    private final BotUtilityService botUtilityService;
    private final AdminService adminService;
    private final PollService pollService;
    private final LedikomBot ledikomBot;

    private SendMessageWithPhotoCallback sendMessageWithPhotoCallback;
    private GetFileFromBotCallback getFileFromBotCallback;
    private SendCouponCallback sendCouponCallback;
    private SendMessageCallback sendMessageCallback;
    private EditMessageCallback editMessageCallback;

    public BotService(final UserService userService, final CouponService couponService, final BotUtilityService botUtilityService, final AdminService adminService, final PollService pollService, @Lazy final LedikomBot ledikomBot) {
        this.userService = userService;
        this.couponService = couponService;
        this.botUtilityService = botUtilityService;
        this.adminService = adminService;
        this.pollService = pollService;
        this.ledikomBot = ledikomBot;
    }

    @PostConstruct
    public void initCallbacks() {
        this.sendMessageWithPhotoCallback = ledikomBot.getSendMessageWithPhotoCallback();
        this.getFileFromBotCallback = ledikomBot.getGetFileFromBotCallback();
        this.sendCouponCallback = ledikomBot.getSendCouponCallback();
        this.sendMessageCallback = ledikomBot.getSendMessageCallback();
        this.editMessageCallback = ledikomBot.getEditMessageCallback();
    }

    @Scheduled(fixedRate = 1000)
    public void processCouponsInMap() {
        CouponService.userCoupons.forEach(this::updateCouponTimerAndMessage);
        CouponService.userCoupons.entrySet().removeIf(userCoupon -> userCoupon.getValue().getExpiryTimestamp() < System.currentTimeMillis() - 5000);
    }

    @Scheduled(fixedRate = 10000)
    public void sendPollInfoToAdmin() {
        SendMessage sm = botUtilityService.buildSendMessage(pollService.getPollsInfoForAdmin(), adminId);
        sendMessageCallback.execute(sm);
    }

    private void updateCouponTimerAndMessage(final UserCouponKey userCouponKey, final UserCouponRecord userCouponRecord) {
        long timeLeftInSeconds = (userCouponRecord.getExpiryTimestamp() - System.currentTimeMillis()) / 1000;
        if (timeLeftInSeconds >= 0) {
            editMessageCallback.execute(userCouponKey.getChatId(), userCouponKey.getMessageId(), BotResponses.updatedCouponText(userCouponRecord, timeLeftInSeconds));
        } else {
            editMessageCallback.execute(userCouponKey.getChatId(), userCouponKey.getMessageId(), BotResponses.couponExpiredMessage());
        }
    }

    public void processAdminRequest(final Update update) {
        RequestFromAdmin requestFromAdmin = adminService.getRequestFromAdmin(update, getFileFromBotCallback);
        if (requestFromAdmin.isPoll()) {
            executeAdminActionOnPollReceived(requestFromAdmin.getPoll());
        } else {
            executeAdminActionOnMessageReceived(requestFromAdmin);
        }
    }

    public void processPoll(final Poll poll) {
        userService.processPoll(poll);
    }

    public void processRefLinkOnFollow(final String command, final Long chatId) {
        if (!command.endsWith("/start")) {
            String refCode = command.substring(7);
            userService.addNewRefUser(Long.parseLong(refCode), chatId);
        }
        addUserAndSendHelloMessage(chatId);
    }

    private void addUserAndSendHelloMessage(final long chatId) {
        if (!userService.userExistsByChatId(chatId)) {
            userService.addNewUser(chatId);
            var sm = botUtilityService.buildSendMessage(BotResponses.startMessage(), chatId);
            Coupon coupon = couponService.findByName(helloCouponName);
            couponService.addCouponButton(sm, coupon, "Активировать приветственный купон", "couponPreview_");
            sendMessageCallback.execute(sm);
        }
    }

    public void sendCouponAcceptMessageIfNotUsed(final String couponCommand, final long chatId) {
        User user = userService.findByChatId(chatId);
        Coupon coupon = couponService.findCouponForUser(user, couponCommand);

        SendMessage sm;
        if (coupon != null) {
            sm = botUtilityService.buildSendMessage(BotResponses.couponAcceptMessage(couponDurationInMinutes), chatId);
            couponService.addCouponButton(sm, coupon, "Активировать", "couponAccept_");
        } else {
            sm = botUtilityService.buildSendMessage(BotResponses.couponUsedOrGloballyExpiredMessage(), chatId);
        }
        sendMessageCallback.execute(sm);
    }

    public void sendCouponIfNotUsed(final String couponCommand, final Long chatId) {
        User user = userService.findByChatId(chatId);
        Coupon coupon = couponService.findCouponForUser(user, couponCommand);

        if (coupon == null) {
            var sm = botUtilityService.buildSendMessage(BotResponses.couponUsedOrGloballyExpiredMessage(), chatId);
            sendMessageCallback.execute(sm);
        } else {
            String couponTextWithUniqueSign = couponService.generateSignedCouponText(coupon);
            var sm = botUtilityService.buildSendMessage(BotResponses.initialCouponText(couponTextWithUniqueSign, couponDurationInMinutes), chatId);
            UserCouponKey userCouponKey = sendCouponCallback.execute(sm);
            couponService.addCouponToMap(userCouponKey, couponTextWithUniqueSign);
            userService.removeCouponFromUser(user, coupon);
        }
    }

    public void sendAllCouponsList(final Long chatId) {
        User user = userService.findByChatId(chatId);
        Set<Coupon> userCoupons = user.getCoupons();

        SendMessage sm;
        if (userCoupons.isEmpty()) {
            sm = botUtilityService.buildSendMessage(BotResponses.noActiveCouponsMessage(), chatId);
        } else {
            sm = botUtilityService.buildSendMessage(BotResponses.listOfCouponsMessage(), chatId);
            sm.setReplyMarkup(couponService.createListOfCoupons(userCoupons));
        }
        sendMessageCallback.execute(sm);
    }

    public void sendReferralLinkForUser(final Long chatId) {
        String refLink = "https://t.me/" + botUsername + "?start=" + chatId;
        sendMessageCallback.execute(botUtilityService.buildSendMessage(BotResponses.referralMessage(refLink, userService.findByChatId(chatId).getReferralCount()), chatId));
    }

    public void sendTriggerReceiveNewsMessage(final Long chatId) {
        User user = userService.findByChatId(chatId);
        user.setReceiveNews(!user.getReceiveNews());
        userService.saveUser(user);
        sendMessageCallback.execute(botUtilityService.buildSendMessage(BotResponses.triggerReceiveNewsMessage(user), chatId));
    }

    private void sendNewsToUsers(final String photoPath, final List<String> splitStringsFromAdminMessage) {
        NewsFromAdmin newsFromAdmin = adminService.getNewsByAdmin(splitStringsFromAdminMessage, photoPath);
        List<User> usersToSendNews = userService.getAllUsersToReceiveNews();

        if (newsFromAdmin.getPhotoPath() == null || newsFromAdmin.getPhotoPath().isBlank()) {
            usersToSendNews.forEach(user -> sendMessageCallback.execute(botUtilityService.buildSendMessage(newsFromAdmin.getNews(), user.getChatId())));
        } else {
            usersToSendNews.forEach(user -> sendMessageWithPhotoCallback.execute(photoPath, newsFromAdmin.getNews(), user.getChatId()));
        }
    }

    private void sendPollToUsers(final Poll poll) {
        List<User> usersToSendNews = userService.getAllUsersToReceiveNews();
        usersToSendNews.forEach(user -> sendMessageCallback.execute(botUtilityService.buildSendPoll(poll, user.getChatId())));
    }

    private void executeAdminActionOnMessageReceived(final RequestFromAdmin requestFromAdmin) {
        List<String> splitStringsFromAdminMessage = adminService.getSplitStrings(requestFromAdmin.getMessage());

        if (splitStringsFromAdminMessage.get(0).equals(AdminMessageToken.NEWS.label)) {
            sendNewsToUsers(requestFromAdmin.getPhotoPath(), splitStringsFromAdminMessage);
        }
    }

    private void executeAdminActionOnPollReceived(final Poll poll) {
        com.ledikom.model.Poll entityPoll = pollService.tgPollToLedikomPoll(poll);
        entityPoll.setLastVoteTimestamp(LocalDateTime.now());
        pollService.savePoll(entityPoll);
        sendPollToUsers(poll);
    }
}
