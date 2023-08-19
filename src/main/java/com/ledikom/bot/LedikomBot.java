package com.ledikom.bot;

import com.ledikom.callback.*;
import com.ledikom.model.UserCouponKey;
import com.ledikom.service.CouponService;
import com.ledikom.service.UserService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.File;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;


@Component
public class LedikomBot extends TelegramLongPollingBot {

    @Value("${bot.username}")
    private String botUsername;
    @Value("${bot.token}")
    private String botToken;
    @Value("${admin.id}")
    private Long adminId;
    @Value("${hello-coupon.name}")
    private String helloCouponName;
    @Value("${coupon.duration-in-minutes}")
    private int couponDurationInMinutes;

    private BotService botService;
    private final UserService userService;
    private final CouponService couponService;

    private static final Logger log = LoggerFactory.getLogger(LedikomBot.class);
    private static final Map<Predicate<String>, ChatIdCallback> chatIdActions = new HashMap<>();
    private static final Map<Predicate<String>, CommandWithChatIdCallback> commandWithChatIdActions = new HashMap<>();

    public LedikomBot(final UserService userService, final CouponService couponService) {
        this.userService = userService;
        this.couponService = couponService;
    }

    @Scheduled(fixedRate = 1000)
    public void processCouponsInMap() {
        Optional.ofNullable(botService).ifPresent(BotService::processCouponsInMap);
    }

    @Autowired
    public void setBotService() {
        this.botService = BotService.getInstance(userService, couponService,
                this::sendImageWithCaption, this::getFileFromBot, this::sendCoupon, this::sendMessage, this::editMessage,
                botToken, botUsername, helloCouponName, couponDurationInMinutes);

        commandWithChatIdActions.put(cmd -> cmd.startsWith("couponPreview_"),
                botService::generateCouponAcceptMessageIfNotUsed);
        commandWithChatIdActions.put(cmd -> cmd.startsWith("couponAccept_"),
                botService::generateCouponIfNotUsed);
        commandWithChatIdActions.put(cmd -> cmd.startsWith("/start"),
                botService::processRefLinkFollowing);
        chatIdActions.put(cmd -> cmd.equals("/kupony"),
                botService::showAllCoupons);
        chatIdActions.put(cmd -> cmd.equals("/moya_ssylka"),
                botService::getReferralLinkForUser);
        chatIdActions.put(cmd -> cmd.equals("/vkl_otkl_rassylku"),
                botService::generateTriggerReceiveNewsMessage);
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
        botService.processAdminMessage(update);
    }

    //    kupony - Мои активные купоны
    //    moya_ssylka - Моя реферальная ссылка
    //    vkl_otkl_rassylku - Вкл/Откл рассылку новостей
    private void processMessage(String command, Long chatId) {
        Optional<ChatIdCallback> chatIdCallback = chatIdActions.entrySet().stream()
                .filter(entry -> entry.getKey().test(command))
                .map(Map.Entry::getValue)
                .findFirst();
        boolean isChatIdAction = chatIdCallback.isPresent();
        if (isChatIdAction) {
            chatIdCallback.get().execute(chatId);
        } else {
            commandWithChatIdActions.entrySet().stream()
                    .filter(entry -> entry.getKey().test(command))
                    .map(Map.Entry::getValue)
                    .findFirst()
                    .ifPresent(action -> action.execute(command, chatId));
        }
    }

    private File getFileFromBot(final GetFile getFileRequest) throws TelegramApiException {
        return execute(getFileRequest);
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
}
