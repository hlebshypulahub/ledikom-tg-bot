package com.ledikom.callback;

@FunctionalInterface
public interface SendMessageWithPhotoByChatIdCallback {
    void execute(String imageUrl, String caption, Long chatId);
}
