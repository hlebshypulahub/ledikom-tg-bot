package com.ledikom.callback;

import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

@FunctionalInterface
public interface SendMessageCallback {
    void execute(SendMessage sm);
}
