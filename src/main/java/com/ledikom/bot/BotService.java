package com.ledikom.bot;

import com.ledikom.model.Coupon;
import com.ledikom.model.User;
import com.ledikom.model.UserCouponKey;
import com.ledikom.model.UserCouponRecord;
import com.ledikom.service.CouponService;
import com.ledikom.service.UserService;
import com.ledikom.utils.BotResponses;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

@Service
@Setter
@Getter
public class BotService {

    public static final Map<UserCouponKey, UserCouponRecord> userCoupons = new HashMap<>();

    @Value("${hello-coupon.name}")
    private String helloCouponName;
    @Value("${coupon.time-in-minutes}")
    private int couponTimeInMinutes;
    @Value("${bot_username}")
    private String botUsername;

    private final UserService userService;
    private final CouponService couponService;

    public BotService(final UserService userService, final CouponService couponService) {
        this.userService = userService;
        this.couponService = couponService;
    }

    public SendMessage addUserAndGenerateHelloMessage(final long chatId) {
        SendMessage sm = null;

        if (!userService.userExistsByChatId(chatId)) {
            userService.addNewUser(chatId);

            sm = SendMessage.builder()
                    .chatId(chatId)
                    .text(BotResponses.startMessage()).build();

            Coupon coupon = couponService.findByName(helloCouponName);

            addCouponButton(sm, coupon, "Активировать приветственный купон", "couponPreview_");
        }

        return sm;
    }

    public SendMessage generateCouponAcceptMessageIfNotUsed(final String couponCommand, final long chatId) {
        User user = userService.findByChatId(chatId);
        Coupon coupon = couponService.findCouponForUser(user, couponCommand);

        if (coupon != null) {
            var sm = SendMessage.builder()
                    .chatId(chatId)
                    .text(BotResponses.couponAcceptMessage(couponTimeInMinutes)).build();
            addCouponButton(sm, coupon, "Активировать", "couponAccept_");
            return sm;
        }

        return SendMessage.builder()
                .chatId(chatId)
                .text(BotResponses.couponUsedMessage()).build();
    }

    public Coupon generateCouponIfNotUsed(final String couponCommand, final Long chatId) {
        User user = userService.findByChatId(chatId);
        Coupon coupon = couponService.findCouponForUser(user, couponCommand);

        if (coupon != null) {
            userService.removeCouponFromUser(user, coupon);
        }
        return coupon;
    }

    private void addCouponButton(final SendMessage sm, final Coupon coupon, final String buttonText, final String callbackData) {
        var markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        var button = new InlineKeyboardButton();
        button.setText(buttonText);
        button.setCallbackData(callbackData + coupon.getId());
        List<InlineKeyboardButton> row = new ArrayList<>();
        row.add(button);
        keyboard.add(row);
        markup.setKeyboard(keyboard);
        sm.setReplyMarkup(markup);
    }

    public void addCouponToMap(final UserCouponKey userCouponKey, final String couponText) {
        long expiryTimestamp = System.currentTimeMillis() + couponTimeInMinutes * 60 * 1000L;
        userCoupons.put(userCouponKey, new UserCouponRecord(expiryTimestamp, couponText));
    }

    public String generateCouponText(final UserCouponRecord userCouponRecord, final long timeLeftInSeconds) {
        return "Времени осталось: " + timeLeftInSeconds / 60 + ":" + (timeLeftInSeconds % 60 < 10 ? "0" + timeLeftInSeconds % 60 : timeLeftInSeconds % 60) +
                "\n\n" +
                userCouponRecord.getText();
    }

    public String generateSignedCoupon(final Coupon coupon) {
        ZoneId moscowZone = ZoneId.of("Europe/Moscow");
        LocalDateTime zonedDateTime = LocalDateTime.now(moscowZone).plusMinutes(couponTimeInMinutes);

        String timeSign = convertIntToTimeInt(zonedDateTime.getDayOfMonth()) + "." + convertIntToTimeInt(zonedDateTime.getMonthValue()) + "." + zonedDateTime.getYear()
                + " " + convertIntToTimeInt(zonedDateTime.getHour()) + ":" + convertIntToTimeInt(zonedDateTime.getMinute()) + ":" + convertIntToTimeInt(zonedDateTime.getSecond());

        // TODO: use bold font?
        String sign = "Действителен до " + timeSign;

        return coupon.getText() + "\n\n" + sign;
    }

    public String convertIntToTimeInt(int value) {
        return value < 10 ? "0" + value : "" + value;
    }

    public void removeExpiredCouponsFromMap() {
        userCoupons.entrySet().removeIf(userCoupon -> userCoupon.getValue().getExpiryTimestamp() < System.currentTimeMillis() - 5000);
    }

    public SendMessage showAllCoupons(final Long chatId) {
        User user = userService.findByChatId(chatId);
        Set<Coupon> userCoupons = user.getCoupons();
        var sm = new SendMessage();

        if (userCoupons.isEmpty()) {
            sm = SendMessage.builder()
                    .chatId(chatId)
                    .text("У вас нету купонов").build();
        } else {
            sm = SendMessage.builder()
                    .chatId(chatId)
                    .text("Ваши купоны:").build();

            sm.setReplyMarkup(createListOfCoupons(userCoupons));
        }

        return sm;
    }

    private InlineKeyboardMarkup createListOfCoupons(final Set<Coupon> coupons) {
        var markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        for (Coupon coupon : coupons) {
            var button = new InlineKeyboardButton();
            button.setText(coupon.getName() + " " + coupon.getDiscount() + "%");
            button.setCallbackData("couponPreview_" + coupon.getId());
            List<InlineKeyboardButton> row = new ArrayList<>();
            row.add(button);
            keyboard.add(row);
        }

        markup.setKeyboard(keyboard);

        return markup;
    }

    public void processRefLink(final String command, final Long chatId) {
        if (!command.endsWith("/start")) {
            String refCode = command.substring(7);
            userService.addNewRefUser(Long.parseLong(refCode), chatId);
        }
    }

    public SendMessage getReferralLinkForUser(final Long chatId) {
        String refLink = "https://t.me/" + botUsername + "?start=" + chatId;

        return SendMessage.builder()
                .chatId(chatId)
                .text(BotResponses.referralMessage(refLink, userService.findByChatId(chatId).getReferralCount())).build();
    }

    public SendMessage generateTriggerReceiveNewsMessage(final Long chatId) {
        User user = userService.findByChatId(chatId);
        user.setReceiveNews(!user.getReceiveNews());
        userService.saveUser(user);

        return SendMessage.builder()
                .chatId(chatId)
                .text("Подписка на рассылку новостей и акций " + (user.getReceiveNews() ? "включена." : "отключена.")).build();
    }
}
