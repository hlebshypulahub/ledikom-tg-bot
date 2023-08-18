package com.ledikom.callback;

import com.ledikom.model.UserCouponKey;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

@FunctionalInterface
public interface SendCouponByChatIdCallback {
    UserCouponKey execute(SendMessage sm);
}
