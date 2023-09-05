package com.ledikom.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
public class EventCollector {

    @Id
    private LocalDateTime timestamp;
    private int coupon;
    private int helloCoupon;
    private int dateCoupon;
    private int refLink;
    private int music;
    private int newUser;
    private int promotion;
    private int poll;
    private int date;
    private int note;
    private int city;
    private int newsDisabled;

    public void reset() {
        timestamp = null;
        coupon = 0;
        helloCoupon = 0;
        dateCoupon = 0;
        refLink = 0;
        music = 0;
        newUser = 0;
        promotion = 0;
        poll = 0;
        date = 0;
        note = 0;
        city = 0;
        newsDisabled = 0;
    }

    public void incrementCoupon() {
        coupon = coupon + 1;
    }

    public void incrementHelloCoupon() {
        helloCoupon = helloCoupon + 1;
    }

    public void incrementDateCoupon() {
        dateCoupon = dateCoupon + 1;
    }

    public void incrementRefLink() {
        refLink = refLink + 1;
    }

    public void incrementMusic() {
        music = music + 1;
    }

    public void incrementNewUser() {
        newUser = newUser + 1;
    }

    public void incrementPromotion() {
        promotion = promotion + 1;
    }

    public void incrementPoll() {
        poll = poll + 1;
    }

    public void incrementDate() {
        date = date + 1;
    }

    public void incrementNote() {
        note = note + 1;
    }

    public void incrementCity() {
        city = city + 1;
    }

    public void incrementNewsDisabled() {
        newsDisabled = newsDisabled + 1;
    }

    public void decrementNewsDisabled() {
        newsDisabled = newsDisabled - 1;
    }

    public String messageToAdmin() {
        return "Счетчик новых событий:\n\n" +
                coupon + " - Активированные купоны: " + "\n" +
                helloCoupon + " - Активированные приветственные купоны\n" +
                dateCoupon + " - Активированные купоны Особенная Дата\n" +
                refLink + " - Переход по реферальной ссылке\n" +
                music + " - Музыка для сна\n" +
                newUser + " - Новые пользователи\n" +
                promotion + " - Участие в промоакции\n" +
                poll + " - Участие в опросах\n" +
                date + " - Настройка особенной даты\n" +
                note + " - Настройка заметок\n" +
                city + " - Настройка города\n" +
                newsDisabled + " - Отключение рассылки\n";
    }
}
