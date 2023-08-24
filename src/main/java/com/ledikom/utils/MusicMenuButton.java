package com.ledikom.utils;

public enum MusicMenuButton {
    RAIN("Дождь", "music_rain");

    public final String buttonText;
    public final String callbackDataString;

    MusicMenuButton(String buttonText, String callbackDataString) {
        this.buttonText = buttonText;
        this.callbackDataString = callbackDataString;
    }
}
