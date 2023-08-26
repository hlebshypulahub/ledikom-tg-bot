package com.ledikom.service;

import com.ledikom.bot.LedikomBot;
import com.ledikom.callback.*;
import com.ledikom.model.MessageIdInChat;
import com.ledikom.model.UserCouponRecord;
import com.ledikom.utils.BotResponses;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;

import java.time.LocalDateTime;

@Service
public class ScheduleService {

    private static final int DELETION_EPSILON_SECONDS = 5;

    @Value("${admin.id}")
    private Long adminId;

    private final BotUtilityService botUtilityService;
    private final PollService pollService;
    private final LedikomBot ledikomBot;

    public ScheduleService(final BotUtilityService botUtilityService, final PollService pollService, final LedikomBot ledikomBot) {
        this.botUtilityService = botUtilityService;
        this.pollService = pollService;
        this.ledikomBot = ledikomBot;
    }

    private SendMessageCallback sendMessageCallback;
    private EditMessageCallback editMessageCallback;
    private DeleteMessageCallback deleteMessageCallback;

    @PostConstruct
    public void initCallbacks() {
        this.sendMessageCallback = ledikomBot.getSendMessageCallback();
        this.editMessageCallback = ledikomBot.getEditMessageCallback();
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
    public void processMessagesToDeleteInMap() {
        LocalDateTime checkpointTimestamp = LocalDateTime.now().plusSeconds(DELETION_EPSILON_SECONDS);
        BotService.messaegsToDeleteMap.entrySet().removeIf(entry -> {
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
}
