package com.ledikom.bot;

import com.ledikom.callback.*;
import com.ledikom.model.UserCouponKey;
import com.ledikom.service.BotService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.botapimethods.BotApiMethodMessage;
import org.telegram.telegrambots.meta.api.methods.polls.SendPoll;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.File;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.polls.Poll;
import org.telegram.telegrambots.meta.api.objects.polls.PollOption;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;


@Component
public class LedikomBot extends TelegramLongPollingBot {

    @Value("${bot.username}")
    private String botUsername;
    @Value("${bot.token}")
    private String botToken;
    @Value("${admin.id}")
    private Long adminId;

    private final BotService botService;

    private static final Logger log = LoggerFactory.getLogger(LedikomBot.class);
    private static final Map<Predicate<String>, ChatIdCallback> chatIdActions = new HashMap<>();
    private static final Map<Predicate<String>, CommandWithChatIdCallback> commandWithChatIdActions = new HashMap<>();

    public LedikomBot(@Lazy final BotService botService) {
        this.botService = botService;
    }

    @PostConstruct
    public void fillActionsMap() {
        commandWithChatIdActions.put(cmd -> cmd.startsWith("couponPreview_"),
                this.botService::sendCouponAcceptMessageIfNotUsed);
        commandWithChatIdActions.put(cmd -> cmd.startsWith("couponAccept_"),
                this.botService::sendCouponIfNotUsed);
        commandWithChatIdActions.put(cmd -> cmd.startsWith("/start"),
                this.botService::processRefLinkOnFollow);
        chatIdActions.put(cmd -> cmd.equals("/kupony"),
                this.botService::sendAllCouponsList);
        chatIdActions.put(cmd -> cmd.equals("/moya_ssylka"),
                this.botService::sendReferralLinkForUser);
        chatIdActions.put(cmd -> cmd.equals("/vkl_otkl_rassylku"),
                this.botService::sendTriggerReceiveNewsMessage);
    }

    public SendMessageWithPhotoCallback getSendMessageWithPhotoCallback() {
        return this::sendImageWithCaption;
    }

    public GetFileFromBotCallback getGetFileFromBotCallback() {
        return this::getFileFromBot;
    }

    public SendCouponCallback getSendCouponCallback() {
        return this::sendCoupon;
    }

    public SendMessageCallback getSendMessageCallback() {
        return this::sendMessage;
    }

    public EditMessageCallback getEditMessageCallback() {
        return this::editMessage;
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
        botService.processAdminRequest(update);
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

    private void sendMessage(BotApiMethodMessage message) {
        try {
            if (message != null) {
                execute(message);
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
