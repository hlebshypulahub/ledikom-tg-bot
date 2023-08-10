package com.ledikom.user;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalTime;

@Getter
@Setter
public class User {
    private Long chatId;
    private LocalTime couponStartTime;
    private Integer couponMessageId;
    private boolean isCouponUsed;

    public User(Long chatId, LocalTime couponStartTime, boolean isCouponUsed) {
        this.chatId = chatId;
        this.couponStartTime = couponStartTime;
        this.isCouponUsed = isCouponUsed;
    }
}
