package com.ledikom.service;

import com.ledikom.callback.GetFileFromBotCallback;
import com.ledikom.model.RequestFromAdmin;
import com.ledikom.model.NewsFromAdmin;
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

    public NewsFromAdmin getNewsByAdmin(final List<String> splitStringsFromAdminMessage, final String photoPath) {
        return new NewsFromAdmin(splitStringsFromAdminMessage.size() > 1 ? splitStringsFromAdminMessage.get(1) : "", photoPath);
    }

    public RequestFromAdmin getRequestFromAdmin(final Update update, final GetFileFromBotCallback getFileFromBotCallback) {
        RequestFromAdmin requestFromAdmin = new RequestFromAdmin();

        var msg = update.getMessage();
        String photoPath;
        if (msg.hasPhoto()) {
            photoPath = botUtilityService.getPhotoFromUpdate(msg, getFileFromBotCallback);
            requestFromAdmin.setPhotoPath(photoPath);
            if (photoPath != null) {
                requestFromAdmin.setMessage(msg.getCaption());
            }
        } else if (msg.hasText()) {
            requestFromAdmin.setMessage(msg.getText());
        } else if (msg.hasPoll()) {
            requestFromAdmin.setPoll(msg.getPoll());
        }

        return requestFromAdmin;
    }
}
