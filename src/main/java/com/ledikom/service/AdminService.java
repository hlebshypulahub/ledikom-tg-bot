package com.ledikom.service;

import com.ledikom.callback.GetFileFromBotCallback;
import com.ledikom.model.MessageFromAdmin;
import com.ledikom.model.NewFromAdmin;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
public class AdminService {

    public static final String DELIMITER = "&";

    private final BotUtilityService botUtilityService;

    public AdminService(final BotUtilityService botUtilityService) {
        this.botUtilityService = botUtilityService;
    }

    public List<String> getSplitStrings(final String messageFromAdmin) {
        List<String> splitStringsFromAdminMessage = new ArrayList<>(Arrays.stream(messageFromAdmin.split(DELIMITER)).map(String::trim).toList());
        splitStringsFromAdminMessage.set(0, splitStringsFromAdminMessage.get(0).toLowerCase());
        return splitStringsFromAdminMessage;
    }

    public NewFromAdmin getNewsByAdmin(final List<String> splitStringsFromAdminMessage, final String photoPath) {
        return new NewFromAdmin(splitStringsFromAdminMessage.size() > 1 ? splitStringsFromAdminMessage.get(1) : "", photoPath);
    }

    public MessageFromAdmin getMessageByAdmin(final Update update, final GetFileFromBotCallback getFileFromBotCallback) {
        MessageFromAdmin messageFromAdmin = new MessageFromAdmin();

        var msg = update.getMessage();
        String photoPath;
        if (msg.hasPhoto()) {
            photoPath = botUtilityService.getPhotoFromUpdate(msg, getFileFromBotCallback);
            messageFromAdmin.setPhotoPath(photoPath);
            if (photoPath != null) {
                messageFromAdmin.setMessage(msg.getCaption());
            }
        } else if (msg.hasText()) {
            messageFromAdmin.setMessage(msg.getText());
        }

        return messageFromAdmin;
    }
}
