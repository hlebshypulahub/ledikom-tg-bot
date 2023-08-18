package com.ledikom.service;

import com.ledikom.model.Coupon;
import com.ledikom.model.User;
import com.ledikom.repository.UserRepository;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserService {
    private static final int INIT_REFERRAL_COUNT = 0;
    private static boolean INIT_RECEIVE_NEWS = true;

    private final UserRepository userRepository;
    private final CouponService couponService;

    public UserService(final UserRepository userRepository, @Lazy final CouponService couponService) {
        this.userRepository = userRepository;
        this.couponService = couponService;
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public void saveAll(final List<User> users) {
        userRepository.saveAll(users);
    }

    public void addNewUser(final Long chatId) {
        User user = new User(chatId, INIT_REFERRAL_COUNT, INIT_RECEIVE_NEWS);
        userRepository.save(user);

        couponService.addCouponsToUser(user);
    }

    public void saveUser(final User user) {
        userRepository.save(user);
    }

    public User findByChatId(final Long chatId) {
        return userRepository.findByChatId(chatId).orElseThrow(() -> new RuntimeException("User not found"));
    }

    public void removeCouponFromUser(final User user, final Coupon coupon) {
        user.getCoupons().remove(coupon);
        userRepository.save(user);
    }

    public void addNewRefUser(final long chatIdFromRefLink, final long chatId) {
        if (chatIdFromRefLink != chatId && !userExistsByChatId(chatId)) {
            User user = userRepository.findByChatId(chatIdFromRefLink).orElseThrow(() -> new RuntimeException("User not found"));
            user.setReferralCount(user.getReferralCount() + 1);
            userRepository.save(user);
        }
    }

    public boolean userExistsByChatId(final long chatId) {
        return userRepository.findByChatId(chatId).isPresent();
    }

    public List<User> getAllUsersToSendNews() {
        return userRepository.findAll().stream().filter(User::getReceiveNews).collect(Collectors.toList());
    }
}
