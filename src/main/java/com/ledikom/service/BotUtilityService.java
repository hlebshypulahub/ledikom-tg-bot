package com.ledikom.service;

import com.ledikom.callback.GetFileFromBotCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.File;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.PhotoSize;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.Comparator;

@Component
public class BotUtilityService {

    @Value("${bot.token}")
    private String botToken;

    public String getPhotoFromUpdate(final Message msg, final GetFileFromBotCallback getFileFromBotCallback) {
        PhotoSize photo = msg.getPhoto().stream()
                .max(Comparator.comparingInt(PhotoSize::getWidth))
                .orElse(null);
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
}
