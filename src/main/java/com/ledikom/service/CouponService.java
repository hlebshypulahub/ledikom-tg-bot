package com.ledikom.service;

import com.ledikom.model.Coupon;
import com.ledikom.model.User;
import com.ledikom.repository.CouponRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CouponService {

    @Value("${hello-coupon.discount}")
    private int helloCouponDiscount;
    @Value("${hello-coupon.name}")
    private String helloCouponName;
    private final static String HELLO_COUPON_TEXT = "Приветственный купон -5% на вашу следующую покупку.";

    private final CouponRepository couponRepository;
    private final UserService userService;

    public CouponService(final CouponRepository couponRepository, final UserService userService) {
        this.couponRepository = couponRepository;
        this.userService = userService;
    }

    @PostConstruct
    public void createHelloCoupon() {
        Coupon coupon = new Coupon(HELLO_COUPON_TEXT, helloCouponName, helloCouponDiscount);
        couponRepository.save(coupon);

        List<User> users = userService.getAllUsers();

        if (!users.isEmpty()) {
            users.forEach(user -> user.getCoupons().add(coupon));
            userService.saveAll(users);
        }
    }

    public void addCouponsToUser(final User user) {
        List<Coupon> coupons = couponRepository.findAll();
        user.getCoupons().addAll(coupons);
        userService.saveUser(user);
    }
}
