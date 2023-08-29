package com.ledikom.utils;

public enum MusicMenuButton {
    RAIN("Дождь за окном", "music_rain"),
    FLUTE("Бамбуковая флейта", "music_flute"),
    FIRE("Костёр у реки", "music_fire"),
    MUSICOCEAN("Океан музыки", "music_musicocean"),
    JAZZ("Ночной джаз", "music_jazz"),
    TROPIC("Тропический лес", "music_tropic"),
    PIANO("Вечерний рояль", "music_piano"),
    MOON("Полёт на Луну", "music_moon");


    public final String buttonText;
    public final String callbackDataString;

    MusicMenuButton(String buttonText, String callbackDataString) {
        this.buttonText = buttonText;
        this.callbackDataString = callbackDataString;
    }
}
