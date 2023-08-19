package com.ledikom.callback;

@FunctionalInterface
public interface CommandWithChatIdCallback {
    void execute(String command, Long chatId);
}
