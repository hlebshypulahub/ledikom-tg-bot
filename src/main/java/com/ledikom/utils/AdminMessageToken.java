package com.ledikom.utils;

public enum AdminMessageToken {
    NEWS("news", 2),
    COUPON("coupon", 7);

    public final String label;
    public final int commandSize;

    AdminMessageToken(String label, final int commandSize) {
        this.label = label;
        this.commandSize = commandSize;
    }
}
