package com.ledikom.utils;

public enum MusicMenuButton {
    RAIN("Дождь за окном", "music_rain"),
    FLUTE("Бамбуковая флейта", "music_flute"),
    FIRE("Костёр у реки", "music_fire"),
    JAZZ("Ночной джаз", "music_jazz");

    public final String buttonText;
    public final String callbackDataString;

    MusicMenuButton(String buttonText, String callbackDataString) {
        this.buttonText = buttonText;
        this.callbackDataString = callbackDataString;
    }
}
