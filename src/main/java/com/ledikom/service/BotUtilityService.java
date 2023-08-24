package com.ledikom.service;

import com.ledikom.callback.GetFileFromBotCallback;
import com.ledikom.utils.MusicMenuButton;
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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class BotUtilityService {

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

        if (photo != null) {
            GetFile getFileRequest = new GetFile();
            getFileRequest.setFileId(photo.getFileId());
            try {
                File file = getFileFromBotCallback.execute(getFileRequest);
                return "https://api.telegram.org/file/bot" + botToken + "/" + file.getFilePath();
            } catch (TelegramApiException e) {
                throw new RuntimeException(e);
            }
        }
        return null;
    }

    public SendMessage buildSendMessage(String text, long chatId) {
        return SendMessage.builder()
                .chatId(chatId)
                .text(text).build();
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
        var markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        var button = new InlineKeyboardButton();
        button.setText(MusicMenuButton.RAIN.buttonText);
        button.setCallbackData(MusicMenuButton.RAIN.callbackDataString);
        List<InlineKeyboardButton> row = new ArrayList<>();
        row.add(button);
        keyboard.add(row);
        markup.setKeyboard(keyboard);
        sm.setReplyMarkup(markup);
    }

    public void addMusicDurationButtonsToSendMessage(final SendMessage sm, String musicString) {
        int oneUnitMinutes = 5;

        var markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        List<InlineKeyboardButton> row = new ArrayList<>();
        for (int index = 1; index <= 4; index++) {
            var button = new InlineKeyboardButton();
            button.setText(oneUnitMinutes * index + " мин");
            button.setCallbackData(musicString + "_" + oneUnitMinutes * index);
            row.add(button);
            if (index % 2 == 0) {
                keyboard.add(row);
                row = new ArrayList<>();
            }
        }

        markup.setKeyboard(keyboard);
        sm.setReplyMarkup(markup);
    }
}
