package com.ledikom.utils;

import com.ledikom.model.Coupon;
import com.ledikom.model.User;
import com.ledikom.model.UserCouponRecord;

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

    public static String couponAcceptMessage(final int durationInMinutes) {
        return "Купон действует " + durationInMinutes + " минут. Вы уверены что хотите активировать сейчас? Показать на кассe";
    }

    public static String couponUsedOrGloballyExpiredMessage() {
        return "Coupon used or glb expired!!!!!!!!!!";
    }

    public static String referralMessage(final String refLink, final int referralCount) {
        return "Ваша реферальная ссылка:\n\n\n" + refLink + "\n\n\nКоличество приглашенных вами пользователей: " + referralCount + "\n\n\nПоделитесь с другом и получайте бонусы: НАПИСАТЬ КАКИЕ БОНУСЫ И ДОАБВИТЬ АВТО ФУНКЦИОНАЛ";
    }

    public static String couponExpiredMessage() {
        return "Время вашего купона истекло.";
    }

    public static String triggerReceiveNewsMessage(final User user) {
        return "Подписка на рассылку новостей и акций " + (user.getReceiveNews() ? "включена." : "отключена.");
    }

    public static String listOfCouponsMessage() {
        return "Ваши купоны:";
    }

    public static String noActiveCouponsMessage() {
        return "У вас нету купонов";
    }

    public static String initialCouponText(final String couponTextWithUniqueSign, final long couponDurationInMinutes) {
        return "Времени осталось: " + UtilityHelper.convertIntToTimeInt(couponDurationInMinutes) + ":00" +
                "\n\n" +
                couponTextWithUniqueSign;
    }

    public static String updatedCouponText(final UserCouponRecord userCouponRecord, final long timeLeftInSeconds) {
        return "Времени осталось: " + UtilityHelper.convertIntToTimeInt(timeLeftInSeconds / 60) + ":" + UtilityHelper.convertIntToTimeInt(timeLeftInSeconds % 60) +
                "\n\n" +
                userCouponRecord.getText();
    }

    public static String couponUniqueSign(final String timeSign) {
        return "Действителен до " + timeSign;
    }

    public static String couponButton(final Coupon coupon) {
        return coupon.getName() + " -" + coupon.getDiscount() + "%";
    }

    public static String helloCoupon(final int helloCouponDiscount) {
        return "Приветственный купон -" + helloCouponDiscount + "% на вашу следующую покупку.";
    }

    public static String noteAdded() {
        return "Заметка записана, можете редактировать через меню";
    }

    public static String editNote() {
        return "*Чтобы редактировать заметку скопируйте свою заметку из сообщения выше, вставьте в поле ввода, измените и отправте";
    }

    public static String addNote() {
        return "Введите нотатку и вышлите сообщение";
    }
}
