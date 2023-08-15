package com.ledikom.controller;

import com.ledikom.service.CouponService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class Controller {

    private final CouponService couponService;

    public Controller(final CouponService couponService) {
        this.couponService = couponService;
    }

    @PostMapping("/api/v1/create-hello-coupon")
    public ResponseEntity<String> createHelloCoupon() {
        couponService.createHelloCoupon();
        return new ResponseEntity<>(HttpStatus.OK);
    }
}
