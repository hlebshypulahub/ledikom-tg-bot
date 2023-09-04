package com.ledikom.service;

import com.ledikom.bot.LedikomBot;
import com.ledikom.callback.*;
import com.ledikom.model.*;
import com.ledikom.utils.BotResponses;
import com.ledikom.utils.UtilityHelper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendAudio;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.InputFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.*;

@Component
public class BotService {

    public static final Map<MessageIdInChat, LocalDateTime> messagesToDeleteMap = new HashMap<>();

    private static final Logger LOGGER = LoggerFactory.getLogger(BotService.class);

    @Value("${bot.username}")
    private String botUsername;
    @Value("${coupon.duration-minutes}")
    private int couponDurationInMinutes;
    @Value("${admin.id}")
    private Long adminId;

    private final UserService userService;
    private final CouponService couponService;
    private final BotUtilityService botUtilityService;
    private final PharmacyService pharmacyService;
    private final LedikomBot ledikomBot;

    private SendMessageWithPhotoCallback sendMessageWithPhotoCallback;
    private SendMessageCallback sendMessageCallback;
    private SendMusicFileCallback sendMusicFileCallback;

    public BotService(final UserService userService, final CouponService couponService, final BotUtilityService botUtilityService, final PharmacyService pharmacyService, @Lazy final LedikomBot ledikomBot) {
        this.userService = userService;
        this.couponService = couponService;
        this.botUtilityService = botUtilityService;
        this.pharmacyService = pharmacyService;
        this.ledikomBot = ledikomBot;
    }

    @PostConstruct
    public void initCallbacks() {
        this.sendMessageWithPhotoCallback = ledikomBot.getSendMessageWithPhotoCallback();
        this.sendMessageCallback = ledikomBot.getSendMessageCallback();
        this.sendMusicFileCallback = ledikomBot.getSendMusicFileCallback();
    }

    public void processStartOrRefLinkFollow(final String command, final Long chatId) {
        if (!command.endsWith("/start")) {
            LOGGER.info("Processing ref link following: {}", command);
            String refCode = command.substring(7);
            userService.addNewRefUser(Long.parseLong(refCode), chatId);
        }
        addUserAndSendHelloMessage(chatId);
    }

    public void processMusicRequest(final String command, final Long chatId) {
        LOGGER.info("Processing music request: {}", command);

        MusicCallbackRequest musicCallbackRequest = UtilityHelper.getMusicCallbackRequest(command);

        if (musicCallbackRequest.readyToPlay()) {
            String audioFileName = command + ".mp3";
            InputStream audioInputStream = getClass().getResourceAsStream("/" + audioFileName);
            InputFile audioInputFile = new InputFile(audioInputStream, audioFileName);
            SendAudio sendAudio = new SendAudio(String.valueOf(chatId), audioInputFile);
            LocalDateTime toDeleteTimestamp = LocalDateTime.now().plusMinutes(musicCallbackRequest.getDuration());
            MessageIdInChat messageIdInChatMusic = sendMusicFileCallback.execute(sendAudio);
            LOGGER.info("Message to delete put to map: {}, {}", messageIdInChatMusic, toDeleteTimestamp);
            messagesToDeleteMap.put(messageIdInChatMusic, toDeleteTimestamp);
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

    public void sendMusicMenu(final long chatId) {
        var sm = botUtilityService.buildSendMessage(BotResponses.musicMenu(), chatId);
        botUtilityService.addMusicMenuButtonsToSendMessage(sm);
        sendMessageCallback.execute(sm);
    }

    private void addUserAndSendHelloMessage(final long chatId) {
        if (!userService.userExistsByChatId(chatId)) {
            userService.addNewUser(chatId);
            var sm = botUtilityService.buildSendMessage(BotResponses.startMessage(), chatId);
            botUtilityService.addPreviewCouponButton(sm, couponService.getHelloCoupon(), "Активировать приветственный купон");
            sendMessageCallback.execute(sm);

            sm = botUtilityService.buildSendMessage(BotResponses.chooseYourCity(), chatId);
            pharmacyService.addCitiesButtons(sm);
            sendMessageCallback.execute(sm);
        }
    }

    public void sendCouponAcceptMessage(final String couponCommand, final long chatId) {
        User user = userService.findByChatId(chatId);
        Coupon coupon = couponService.findCouponForUser(user, couponCommand);

        SendMessage sm;
        if (couponService.couponCanBeUsedNow(coupon)) {
            boolean inAllPharmacies = pharmacyService.findAll().size() == coupon.getPharmacies().size();
            sm = botUtilityService.buildSendMessage(BotResponses.couponAcceptMessage(coupon, inAllPharmacies, couponDurationInMinutes), chatId);
            botUtilityService.addAcceptCouponButton(sm, coupon, "Активировать купон");
        } else {
            LOGGER.error("Coupon is not active for user: {}", chatId);
            sm = botUtilityService.buildSendMessage(BotResponses.couponIsNotActive(), chatId);
        }
        sendMessageCallback.execute(sm);
    }

    public void sendActivatedCouponIfCanBeUsed(final String couponCommand, final Long chatId) {
        User user = userService.findByChatId(chatId);
        Coupon coupon = couponService.findCouponForUser(user, couponCommand);

        byte[] barcodeImageByteArray = coupon.getBarcodeImageByteArray();
        InputFile barcodeInputFile = new InputFile(new ByteArrayInputStream(barcodeImageByteArray), "barcode.jpg");

        String couponTextWithBarcodeAndTimeSign = "Действителен до: " + couponService.getTimeSign() + "\n\n" + coupon.getBarcode() + "\n\n" + coupon.getText();

        MessageIdInChat messageIdInChat = sendMessageWithPhotoCallback.execute(barcodeInputFile, BotResponses.initialCouponText(couponTextWithBarcodeAndTimeSign, couponDurationInMinutes), chatId);
        LOGGER.info("Adding coupon to map: {}, {}", messageIdInChat, couponTextWithBarcodeAndTimeSign);
        couponService.addCouponToMap(messageIdInChat, couponTextWithBarcodeAndTimeSign);
        userService.markCouponAsUsedForUser(user, coupon);
    }

    public void sendCityMenu(final long chatId) {
        User user = userService.findByChatId(chatId);
        var sm = botUtilityService.buildSendMessage(BotResponses.yourCityCanUpdate(user.getCity()), chatId);
        pharmacyService.addCitiesButtons(sm);
        sendMessageCallback.execute(sm);
    }

    public void sendPromotionAcceptedMessage(final long chatId) {
        sendMessageCallback.execute(botUtilityService.buildSendMessage(BotResponses.promotionAccepted(), chatId));
    }
}
