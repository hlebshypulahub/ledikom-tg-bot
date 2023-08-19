package com.ledikom.utils;

public class UtilityHelper {

    public static String convertIntToTimeInt(long value) {
        return value < 10 ? "0" + value : "" + value;
    }

}
