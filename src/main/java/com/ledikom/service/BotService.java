package com.ledikom.service;

import com.ledikom.bot.LedikomBot;
import com.ledikom.callback.*;
import com.ledikom.model.*;
import com.ledikom.utils.AdminMessageToken;
import com.ledikom.utils.BotResponses;
import com.ledikom.utils.City;
import com.ledikom.utils.UtilityHelper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendAudio;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.polls.Poll;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.*;

@Component
public class BotService {

    public static final Map<MessageIdInChat, LocalDateTime> messaegsToDeleteMap = new HashMap<>();

    @Value("${bot.username}")
    private String botUsername;
    @Value("${hello-coupon.name}")
    private String helloCouponName;
    @Value("${coupon.duration-minutes}")
    private int couponDurationInMinutes;

    private final UserService userService;
    private final CouponService couponService;
    private final BotUtilityService botUtilityService;
    private final AdminService adminService;
    private final PharmacyService pharmacyService;
    private final LedikomBot ledikomBot;

    private SendMessageWithPhotoCallback sendMessageWithPhotoCallback;
    private GetFileFromBotCallback getFileFromBotCallback;
    private SendCouponCallback sendCouponCallback;
    private SendMessageCallback sendMessageCallback;
    private SendMusicFileCallback sendMusicFileCallback;

    public BotService(final UserService userService, final CouponService couponService, final BotUtilityService botUtilityService, final AdminService adminService, final PharmacyService pharmacyService, @Lazy final LedikomBot ledikomBot) {
        this.userService = userService;
        this.couponService = couponService;
        this.botUtilityService = botUtilityService;
        this.adminService = adminService;
        this.pharmacyService = pharmacyService;
        this.ledikomBot = ledikomBot;
    }

    @PostConstruct
    public void initCallbacks() {
        this.sendMessageWithPhotoCallback = ledikomBot.getSendMessageWithPhotoCallback();
        this.getFileFromBotCallback = ledikomBot.getGetFileFromBotCallback();
        this.sendCouponCallback = ledikomBot.getSendCouponCallback();
        this.sendMessageCallback = ledikomBot.getSendMessageCallback();
        this.sendMusicFileCallback = ledikomBot.getSendMusicFileCallback();
    }

    public void processAdminRequest(final Update update) throws IOException {
        RequestFromAdmin requestFromAdmin = adminService.getRequestFromAdmin(update, getFileFromBotCallback);
        if (requestFromAdmin.isPoll()) {
            adminService.executeAdminActionOnPollReceived(requestFromAdmin.getPoll());
        } else {
            adminService.executeAdminActionOnMessageReceived(requestFromAdmin);
        }
    }

    public void processPoll(final Poll poll) {
        userService.processPoll(poll);
    }

    public void processStartRefLinkOnFollow(final String command, final Long chatId) {
        if (!command.endsWith("/start")) {
            String refCode = command.substring(7);
            userService.addNewRefUser(Long.parseLong(refCode), chatId);
        }
        addUserAndSendHelloMessage(chatId);
    }

    public void processMusicRequest(final String command, final Long chatId) {
        MusicCallbackRequest musicCallbackRequest = UtilityHelper.getMusicCallbackRequest(command);

        if (musicCallbackRequest.readyToPlay()) {
            String audioFileName = command + ".mp3";
            InputStream audioInputStream = getClass().getResourceAsStream("/" + audioFileName);
            InputFile audioInputFile = new InputFile(audioInputStream, audioFileName);
            SendAudio sendAudio = new SendAudio(String.valueOf(chatId), audioInputFile);
            LocalDateTime toDeleteTimestamp = LocalDateTime.now().plusMinutes(musicCallbackRequest.getDuration());
            MessageIdInChat messageIdInChatMusic = sendMusicFileCallback.execute(sendAudio);
            messaegsToDeleteMap.put(messageIdInChatMusic, toDeleteTimestamp);
        } else {
            String imageName = musicCallbackRequest.getStyleString() + ".jpg";
            InputStream audioInputStream = getClass().getResourceAsStream("/" + imageName);
            InputFile inputFile = new InputFile(audioInputStream, imageName);
            sendMessageWithPhotoCallback.execute(inputFile, BotResponses.goodNight(), chatId);
            var sm = SendMessage.builder().chatId(chatId).text(BotResponses.musicDurationMenu()).build();
            botUtilityService.addMusicDurationButtonsToSendMessage(sm, command);
            sendMessageCallback.execute(sm);
        }
    }

    public void processStatefulUserResponse(final String text, final Long chatId) {
        String feedbackMessage = userService.processStatefulUserResponse(text, chatId);
        sendMessageCallback.execute(botUtilityService.buildSendMessage(feedbackMessage, chatId));
    }

    private void addUserAndSendHelloMessage(final long chatId) {
        if (!userService.userExistsByChatId(chatId)) {
            userService.addNewUser(chatId);
            var sm = botUtilityService.buildSendMessage(BotResponses.startMessage(), chatId);
            couponService.addCouponButton(sm, couponService.findByName(helloCouponName), "Активировать приветственный купон", "couponPreview_");
            sendMessageCallback.execute(sm);

            sm = botUtilityService.buildSendMessage(BotResponses.chooseYourCity(), chatId);
            pharmacyService.addCitiesButtons(sm);
            sendMessageCallback.execute(sm);
        }
    }

    public void sendMusicMenu(final long chatId) {
        var sm = botUtilityService.buildSendMessage(BotResponses.musicMenu(), chatId);
        botUtilityService.addMusicMenuButtonsToSendMessage(sm);
        sendMessageCallback.execute(sm);
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
            MessageIdInChat messageIdInChat = sendCouponCallback.execute(sm);
            couponService.addCouponToMap(messageIdInChat, couponTextWithUniqueSign);
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

    public void sendNoteAndSetUserResponseState(final long chatId) {
        List<SendMessage> sendMessageList = userService.processNoteRequestAndBuildSendMessageList(chatId);
        sendMessageList.forEach(sm -> sendMessageCallback.execute(sm));
    }
}
