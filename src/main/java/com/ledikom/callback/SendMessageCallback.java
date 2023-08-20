package com.ledikom.callback;

import org.telegram.telegrambots.meta.api.methods.botapimethods.BotApiMethodMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

@FunctionalInterface
public interface SendMessageCallback {
    void execute(BotApiMethodMessage message);
}
