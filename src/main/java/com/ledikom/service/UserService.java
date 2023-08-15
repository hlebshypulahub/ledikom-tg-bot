package com.ledikom.service;

import com.ledikom.model.Coupon;
import com.ledikom.model.User;
import com.ledikom.repository.UserRepository;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserService {

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
        User user = new User(chatId);
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
}
