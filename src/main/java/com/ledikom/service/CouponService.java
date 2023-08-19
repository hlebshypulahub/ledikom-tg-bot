package com.ledikom.service;

import com.ledikom.model.Coupon;
import com.ledikom.model.User;
import com.ledikom.model.UserCouponKey;
import com.ledikom.model.UserCouponRecord;
import com.ledikom.repository.CouponRepository;
import com.ledikom.utils.BotResponses;
import com.ledikom.utils.UtilityHelper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

@Service
public class CouponService {

    public static final Map<UserCouponKey, UserCouponRecord> userCoupons = new HashMap<>();

    @Value("${hello-coupon.discount}")
    private int helloCouponDiscount;
    @Value("${hello-coupon.name}")
    private String helloCouponName;
    @Value("${coupon.duration-in-minutes}")
    private int couponDurationInMinutes;

    private final CouponRepository couponRepository;
    private final UserService userService;

    public CouponService(final CouponRepository couponRepository, final UserService userService) {
        this.couponRepository = couponRepository;
        this.userService = userService;
    }

    @PostConstruct
    public void createHelloCoupon() {
        Coupon coupon = new Coupon(BotResponses.helloCoupon(helloCouponDiscount), helloCouponName, helloCouponDiscount);
        couponRepository.save(coupon);

        List<User> users = userService.getAllUsers();
        users.forEach(user -> user.getCoupons().add(coupon));
        userService.saveAll(users);
    }

    public Coupon findByName(final String helloCouponName) {
        return couponRepository.findByName(helloCouponName).orElseThrow(() -> new RuntimeException("Coupon not found"));
    }

    public Coupon findCouponForUser(final User user, final String couponCommand) {
        int couponId = Integer.parseInt(couponCommand.split("_")[1]);
        return user.getCoupons().stream().filter(c -> c.getId() == couponId).findFirst().orElse(null);
    }

    public void addCouponsToUser(final User user) {
        List<Coupon> coupons = couponRepository.findAll();
        user.getCoupons().addAll(coupons);
        userService.saveUser(user);
    }

    public void addCouponButton(final SendMessage sm, final Coupon coupon, final String buttonText, final String callbackData) {
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
        long expiryTimestamp = System.currentTimeMillis() + couponDurationInMinutes * 60 * 1000L;
        userCoupons.put(userCouponKey, new UserCouponRecord(expiryTimestamp, couponText));
    }

    public String generateSignedCouponText(final Coupon coupon) {
        ZoneId moscowZone = ZoneId.of("Europe/Moscow");
        LocalDateTime zonedDateTime = LocalDateTime.now(moscowZone).plusMinutes(couponDurationInMinutes);

        String timeSign = UtilityHelper.convertIntToTimeInt(zonedDateTime.getDayOfMonth()) + "." + UtilityHelper.convertIntToTimeInt(zonedDateTime.getMonthValue()) + "." + zonedDateTime.getYear()
                + " " + UtilityHelper.convertIntToTimeInt(zonedDateTime.getHour()) + ":" + UtilityHelper.convertIntToTimeInt(zonedDateTime.getMinute()) + ":" + UtilityHelper.convertIntToTimeInt(zonedDateTime.getSecond());

        return coupon.getText() + "\n\n" + BotResponses.couponUniqueSign(timeSign);
    }

    public InlineKeyboardMarkup createListOfCoupons(final Set<Coupon> coupons) {
        var markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        for (Coupon coupon : coupons) {
            var button = new InlineKeyboardButton();
            button.setText(BotResponses.couponButton(coupon));
            button.setCallbackData("couponPreview_" + coupon.getId());
            List<InlineKeyboardButton> row = new ArrayList<>();
            row.add(button);
            keyboard.add(row);
        }

        markup.setKeyboard(keyboard);

        return markup;
    }
}
