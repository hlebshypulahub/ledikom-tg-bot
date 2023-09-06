package com.ledikom.service;

import com.ledikom.callback.GetFileFromBotCallback;
import com.ledikom.model.Coupon;
import com.ledikom.utils.BotResponses;
import com.ledikom.utils.City;
import com.ledikom.utils.MusicMenuButton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.polls.SendPoll;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.File;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.PhotoSize;
import org.telegram.telegrambots.meta.api.objects.polls.Poll;
import org.telegram.telegrambots.meta.api.objects.polls.PollOption;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class BotUtilityService {

    private static final Logger LOGGER = LoggerFactory.getLogger(BotUtilityService.class);

    @Value("${bot.token}")
    private String botToken;

    public String getPhotoFromUpdate(final Message msg, final GetFileFromBotCallback getFileFromBotCallback) {
        PhotoSize photo = null;
        if (msg.hasPhoto()) {
            photo = msg.getPhoto().stream()
                    .max(Comparator.comparingInt(PhotoSize::getWidth))
                    .orElse(null);
        } else if (msg.hasDocument()) {
            photo = msg.getDocument().getThumbnail();
        }

        LOGGER.info("Photo got from message: {}", photo);

        if (photo != null) {
            GetFile getFileRequest = new GetFile();
            getFileRequest.setFileId(photo.getFileId());
            try {
                File file = getFileFromBotCallback.execute(getFileRequest);
                String filePath = "https://api.telegram.org/file/bot" + botToken + "/" + file.getFilePath();
                LOGGER.info("Photo file path: {}", filePath);
                return filePath;
            } catch (TelegramApiException e) {
                throw new RuntimeException("Error in getting photo file path");
            }
        }

        return null;
    }

    public boolean messageHasPhoto(final Message message) {
        return message.hasPhoto() || message.hasDocument();
    }

    public SendMessage buildSendMessage(String text, long chatId) {
        return SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .parseMode("Markdown")
                .build();
    }

    public SendPoll buildSendPoll(Poll poll, long chatId) {
        return SendPoll.builder()
                .chatId(chatId)
                .question(poll.getQuestion())
                .options(poll.getOptions().stream().map(PollOption::getText).collect(Collectors.toList()))
                .isAnonymous(com.ledikom.model.Poll.IS_ANONYMOUS)
                .type(poll.getType())
                .allowMultipleAnswers(poll.getAllowMultipleAnswers())
                .correctOptionId(poll.getCorrectOptionId())
                .explanation(poll.getExplanation())
                .build();
    }

    public void addMusicMenuButtonsToSendMessage(final SendMessage sm) {
        addButtonsToMessage(sm, 2,
                Arrays.stream(MusicMenuButton.values()).map(value -> value.buttonText).collect(Collectors.toList()),
                Arrays.stream(MusicMenuButton.values()).map(value -> value.callbackDataString).collect(Collectors.toList()));
    }

    public void addMusicDurationButtonsToSendMessage(final SendMessage sm, String musicString) {
        addButtonsToMessage(sm, 2,
                List.of("5 мин \uD83D\uDD51", "10 мин \uD83D\uDD53", "15 мин \uD83D\uDD56", "20 мин \uD83D\uDD59"),
                List.of(musicString + "_5", musicString + "_10", musicString + "+15", musicString + "_20"));
    }

    public void addCitiesButtons(final SendMessage sm, final Set<City> cities) {
        addButtonsToMessage(sm, 2,
                cities.stream().map(city -> city.label + " " + city.logo).collect(Collectors.toList()),
                cities.stream().map(Enum::name).collect(Collectors.toList()));
    }

    public void addAcceptCouponButton(final SendMessage sm, final Coupon coupon, final String buttonText) {
        addButtonToMessage(sm, buttonText, "couponAccept_" + coupon.getId());
    }

    public void addPreviewCouponButton(final SendMessage sm, final Coupon coupon, final String buttonText) {
        addButtonToMessage(sm, buttonText, "couponPreview_" + coupon.getId());
    }

    public void addPromotionAcceptButton(final SendMessage sm) {
        addButtonToMessage(sm, "⭐⭐⭐ Участвовать ⭐⭐⭐", "promotionAccept");
    }

    public InlineKeyboardMarkup createListOfCoupons(final Set<Coupon> coupons) {
        var markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        for (Coupon coupon : coupons) {
            var button = new InlineKeyboardButton();
            button.setText(BotResponses.couponButton(coupon));
            button.setCallbackData("couponPreview_" + coupon.getId());
            List<InlineKeyboardButton> row = new ArrayList<>();
            row.add(button);
            keyboard.add(row);
        }

        markup.setKeyboard(keyboard);

        return markup;
    }

    private void addButtonsToMessage(final SendMessage sm, final int buttonsInRow, final List<String> buttonTextList, final List<String> callbackDataList) {
        var markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        List<InlineKeyboardButton> row = new ArrayList<>();
        for (int index = 0; index < buttonTextList.size(); ) {
            var button = new InlineKeyboardButton();
            button.setText(buttonTextList.get(index));
            button.setCallbackData(callbackDataList.get(index));
            row.add(button);
            if (++index % buttonsInRow == 0 || index == buttonTextList.size()) {
                keyboard.add(row);
                row = new ArrayList<>();
            }
        }

        markup.setKeyboard(keyboard);
        sm.setReplyMarkup(markup);
    }

    private void addButtonToMessage(final SendMessage sm, final String buttonText, final String callbackData) {
        var markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        var button = new InlineKeyboardButton();
        button.setText(buttonText);
        button.setCallbackData(callbackData);
        List<InlineKeyboardButton> row = new ArrayList<>();
        row.add(button);
        keyboard.add(row);
        markup.setKeyboard(keyboard);
        sm.setReplyMarkup(markup);
    }
}
