package com.ledikom;

import com.ledikom.bot.BotService;
import com.ledikom.bot.LedikomBot;
import com.ledikom.service.CouponService;
import com.ledikom.service.UserService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LedikomConfiguration {

//    private final LedikomBot ledikomBot;
//    private final UserService userService;
//    private final CouponService couponService;
//
//    public LedikomConfiguration(final LedikomBot ledikomBot, final UserService userService, final CouponService couponService) {
//        this.ledikomBot = ledikomBot;
//        this.userService = userService;
//        this.couponService = couponService;
//    }
//
//    @Bean
//    public BotService botService() {
//        return new BotService(userService, couponService, ledikomBot.getSendMessageWithPhotoCallback(), ledikomBot.getFileFromBotCallback(), ledikomBot.getSendCouponCallback(), ledikomBot.getSendMessageCallback(), ledikomBot.getEditMessageCallback());
//    }
}
