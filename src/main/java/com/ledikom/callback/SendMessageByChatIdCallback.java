package com.ledikom.callback;

@FunctionalInterface
public interface SendMessageByChatIdCallback {
    void execute(String text, Long chatId);
}
