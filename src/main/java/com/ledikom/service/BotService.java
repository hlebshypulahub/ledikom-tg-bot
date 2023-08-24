package com.ledikom.service;

import com.ledikom.bot.LedikomBot;
import com.ledikom.callback.*;
import com.ledikom.model.*;
import com.ledikom.utils.AdminMessageToken;
import com.ledikom.utils.BotResponses;
import com.ledikom.utils.UtilityHelper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendAudio;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.polls.Poll;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.*;

@Component
public class BotService {

    private static final Map<MessageIdInChat, LocalDateTime> musicMessagesMap = new HashMap<>();
    private static final int DELETION_EPSILON_SECONDS = 5;

    @Value("${bot.username}")
    private String botUsername;
    @Value("${hello-coupon.name}")
    private String helloCouponName;
    @Value("${coupon.duration-minutes}")
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
    private SendMusicFileCallback sendMusicFileCallback;
    private DeleteMessageCallback deleteMessageCallback;

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
        this.sendMusicFileCallback = ledikomBot.getSendMusicFileCallback();
        this.deleteMessageCallback = ledikomBot.getDeleteMessageCallback();
    }

    @Scheduled(fixedRate = 1000)
    public void processCouponsInMap() {
        CouponService.userCoupons.entrySet().removeIf(userCoupon -> {
            updateCouponTimerAndMessage(userCoupon.getKey(), userCoupon.getValue());
            return userCoupon.getValue().getExpiryTimestamp() < System.currentTimeMillis() - 1000 * DELETION_EPSILON_SECONDS;
        });
    }

    @Scheduled(fixedRate = 1000 * 60)
    public void processMusicMessagesInMap() {
        LocalDateTime checkpointTimestamp = LocalDateTime.now().plusSeconds(DELETION_EPSILON_SECONDS);
        musicMessagesMap.entrySet().removeIf(entry -> {
            if (entry.getValue().isBefore(checkpointTimestamp)) {
                DeleteMessage deleteMessage = DeleteMessage.builder()
                        .chatId(entry.getKey().getChatId())
                        .messageId(entry.getKey().getMessageId())
                        .build();
                deleteMessageCallback.execute(deleteMessage);
                return true;
            }
            return false;
        });
        System.out.println(musicMessagesMap.size());
    }

    @Scheduled(fixedRate = 1000 * 60 * 60)
    public void sendPollInfoToAdmin() {
        SendMessage sm = botUtilityService.buildSendMessage(pollService.getPollsInfoForAdmin(), adminId);
        sendMessageCallback.execute(sm);
    }

    private void updateCouponTimerAndMessage(final MessageIdInChat messageIdInChat, final UserCouponRecord userCouponRecord) {
        long timeLeftInSeconds = (userCouponRecord.getExpiryTimestamp() - System.currentTimeMillis()) / 1000;
        if (timeLeftInSeconds >= 0) {
            editMessageCallback.execute(messageIdInChat.getChatId(), messageIdInChat.getMessageId(), BotResponses.updatedCouponText(userCouponRecord, timeLeftInSeconds));
        } else {
            editMessageCallback.execute(messageIdInChat.getChatId(), messageIdInChat.getMessageId(), BotResponses.couponExpiredMessage());
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

    public void processMusicRequest(final String command, final Long chatId) {
        MusicCallbackRequest musicCallbackRequest = UtilityHelper.getMusicCallbackRequest(command);

        if (musicCallbackRequest.readyToPlay()) {
            String audioFileName = command + ".mp3";
            InputStream audioInputStream = getClass().getResourceAsStream("/" + audioFileName);
            InputFile audioInputFile = new InputFile(audioInputStream, audioFileName);
            SendAudio sendAudio = new SendAudio(String.valueOf(chatId), audioInputFile);
            sendAudio.setDuration(musicCallbackRequest.getDuration() * 60);
            MessageIdInChat messageIdInChat = sendMusicFileCallback.execute(sendAudio);
            musicMessagesMap.put(messageIdInChat, LocalDateTime.now().plusMinutes(musicCallbackRequest.getDuration()));
        } else {
            var sm = botUtilityService.buildSendMessage(BotResponses.musicDurationMenu(), chatId);
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
            Coupon coupon = couponService.findByName(helloCouponName);
            couponService.addCouponButton(sm, coupon, "Активировать приветственный купон", "couponPreview_");
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

    public boolean userIsInActiveState(final Long chatId) {
        return userService.userIsInActiveState(chatId);
    }
}
