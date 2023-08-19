package com.ledikom.utils;

public enum AdminMessageToken {
    NEWS("news"),
    COUPON("coupon");

    public final String label;

    AdminMessageToken(String label) {
        this.label = label;
    }
}
