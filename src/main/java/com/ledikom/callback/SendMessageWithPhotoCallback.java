package com.ledikom.callback;

@FunctionalInterface
public interface SendMessageWithPhotoCallback {
    void execute(String imageUrl, String caption, Long chatId);
}
