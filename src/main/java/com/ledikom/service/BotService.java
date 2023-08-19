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

import java.util.*;

@Component
public class BotService {

    @Value("${bot.username}")
    private String botUsername;
    @Value("${hello-coupon.name}")
    private String helloCouponName;
    @Value("${coupon.duration-in-minutes}")
    private int couponDurationInMinutes;

    private final UserService userService;
    private final CouponService couponService;
    private final BotUtilityService botUtilityService;
    private final AdminService adminService;
    private final LedikomBot ledikomBot;

    private SendMessageWithPhotoCallback sendMessageWithPhotoCallback;
    private GetFileFromBotCallback getFileFromBotCallback;
    private SendCouponCallback sendCouponCallback;
    private SendMessageCallback sendMessageCallback;
    private EditMessageCallback editMessageCallback;

    public BotService(final UserService userService, final CouponService couponService, final BotUtilityService botUtilityService, final AdminService adminService, @Lazy final LedikomBot ledikomBot) {
        this.userService = userService;
        this.couponService = couponService;
        this.botUtilityService = botUtilityService;
        this.adminService = adminService;
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

    private void updateCouponTimerAndMessage(final UserCouponKey userCouponKey, final UserCouponRecord userCouponRecord) {
        long timeLeftInSeconds = (userCouponRecord.getExpiryTimestamp() - System.currentTimeMillis()) / 1000;
        if (timeLeftInSeconds >= 0) {
            editMessageCallback.execute(userCouponKey.getChatId(), userCouponKey.getMessageId(), BotResponses.updatedCouponText(userCouponRecord, timeLeftInSeconds));
        } else {
            editMessageCallback.execute(userCouponKey.getChatId(), userCouponKey.getMessageId(), BotResponses.couponExpiredMessage());
        }
    }

    public void processAdminMessage(final Update update) {
        executeAdminActionByCommand(adminService.getMessageByAdmin(update, getFileFromBotCallback));
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

    private void executeAdminActionByCommand(final MessageFromAdmin messageFromAdmin) {
        List<String> splitStringsFromAdminMessage = adminService.getSplitStrings(messageFromAdmin.getMessage());

        if (splitStringsFromAdminMessage.get(0).equals(AdminMessageToken.NEWS.label)) {
            sendNewsToUser(messageFromAdmin.getPhotoPath(), splitStringsFromAdminMessage);
        }
    }

    private void sendNewsToUser(final String photoPath, final List<String> splitStringsFromAdminMessage) {
        NewFromAdmin newFromAdmin = adminService.getNewsByAdmin(splitStringsFromAdminMessage, photoPath);
        List<User> usersToSendNews = userService.getAllUsersToReceiveNews();

        if (newFromAdmin.getPhotoPath() == null || newFromAdmin.getPhotoPath().isBlank()) {
            usersToSendNews.forEach(user -> sendMessageCallback.execute(botUtilityService.buildSendMessage(newFromAdmin.getNews(), user.getChatId())));
        } else {
            usersToSendNews.forEach(user -> sendMessageWithPhotoCallback.execute(photoPath, newFromAdmin.getNews(), user.getChatId()));
        }
    }
}
