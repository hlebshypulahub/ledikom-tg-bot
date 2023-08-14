package com.ledikom.utils;

public final class BotResponses {

    public static String startMessage() {
        return """
                Приветствуем, это бот сетки аптек Ледиком.
                                
                Здесь вы сможете:
                - поставить напоминание о приеме лекарств
                - найти ближайшую аптеку в своем городе
                                
                Также здесь регулярно будут появляться опросы и действует реферальная программа. Будьте активными и
                выигрывайте призы в виде скидок на наши товары.
                                
                Сейчас вы можете активировать купон со скидкой 5% на покупки.
                """;
    }

    public static String couponAcceptMessage(int durationInMinutes) {
        // TODO: update to normal format
        return "Купон действует " + durationInMinutes + " минут. Вы уверены что хотите активировать сейчас? Показать на кассe";
    }

    public static String couponUsedMessage() {
        // TODO: update to normal format
        return "Coupon used!!!!!!!!!!";
    }
}
