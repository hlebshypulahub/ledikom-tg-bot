package com.ledikom.service;

import com.ledikom.bot.LedikomBot;
import com.ledikom.callback.SendMessageCallback;
import com.ledikom.callback.SendMessageWithPhotoCallback;
import com.ledikom.model.*;
import com.ledikom.repository.CouponRepository;
import com.ledikom.utils.BotResponses;
import com.ledikom.utils.UtilityHelper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

@Service
public class CouponService {

    public static final Map<MessageIdInChat, UserCouponRecord> userCoupons = new HashMap<>();

    @Value("${hello-coupon.name}")
    private String helloCouponName;
    @Value("${hello-coupon.text}")
    private String helloCouponText;
    @Value("${hello-coupon.type}")
    private String helloCouponType;
    @Value("${hello-coupon.daily-quantity}")
    private int helloCouponDailyQuantity;
    @Value("${coupon.duration-minutes}")
    private int couponDurationInMinutes;
    @Value("${coupon.secret}")
    private int couponSecret;
    @Value("${admin.id}")
    private long adminId;

    private final CouponRepository couponRepository;
    private final UserService userService;
    private final PharmacyService pharmacyService;
    private final BotUtilityService botUtilityService;
    private final LedikomBot ledikomBot;
    private final RestTemplate restTemplate;

    private SendMessageCallback sendMessageCallback;
    private SendMessageWithPhotoCallback sendMessageWithPhotoCallback;

    public CouponService(final CouponRepository couponRepository, final UserService userService, final PharmacyService pharmacyService, final BotUtilityService botUtilityService, final LedikomBot ledikomBot, final RestTemplate restTemplate) {
        this.couponRepository = couponRepository;
        this.userService = userService;
        this.pharmacyService = pharmacyService;
        this.botUtilityService = botUtilityService;
        this.ledikomBot = ledikomBot;
        this.restTemplate = restTemplate;
    }

    // TODO: remove postconstruct
    @PostConstruct
    public void createHelloCoupon() {
        Coupon coupon = new Coupon(helloCouponName, helloCouponText, helloCouponType, helloCouponDailyQuantity);
        couponRepository.save(coupon);

        List<User> users = userService.getAllUsers();
        users.forEach(user -> user.getCoupons().add(coupon));
        userService.saveAll(users);
    }

    @PostConstruct
    public void initCallbacks() {
        this.sendMessageCallback = ledikomBot.getSendMessageCallback();
        this.sendMessageWithPhotoCallback = ledikomBot.getSendMessageWithPhotoCallback();
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

    public void addCouponToMap(final MessageIdInChat messageIdInChat, final String couponText) {
        long expiryTimestamp = System.currentTimeMillis() + couponDurationInMinutes * 60 * 1000L;
        userCoupons.put(messageIdInChat, new UserCouponRecord(expiryTimestamp, couponText));
    }

    public String generateBarcode(final Coupon coupon) {
        ZoneId moscowZone = ZoneId.of("Europe/Moscow");
        LocalDateTime zonedDateTime = LocalDateTime.now(moscowZone).plusMinutes(couponDurationInMinutes);

        Coupon savedCouponWithIncreasedQuantityUsed = increaseQuantityUsed(coupon);

        return coupon.getType()
                + UtilityHelper.convertIntToTimeInt(zonedDateTime.getDayOfMonth())
                + UtilityHelper.convertIntToTimeInt(zonedDateTime.getMonthValue())
                + (zonedDateTime.getYear())
                + getCouponNumber(savedCouponWithIncreasedQuantityUsed)
                + (Integer.parseInt(coupon.getType()) + zonedDateTime.getDayOfMonth() + zonedDateTime.getMonthValue() + zonedDateTime.getYear() - 2000 + couponSecret);
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

    public void createAndSendNewCoupon(final String photoPath, final List<String> splitStringsFromAdminMessage) throws IOException {
        Coupon coupon = getNewCoupon(splitStringsFromAdminMessage);
        couponRepository.save(coupon);
        List<User> usersForCouponCities = userService.getAllUsersForCouponCities(coupon.getPharmacies());
        usersForCouponCities.forEach(user -> addCouponToUser(coupon, user));

        if (photoPath == null || photoPath.isBlank()) {
            usersForCouponCities.forEach(user -> {
                var sm = botUtilityService.buildSendMessage(BotResponses.newCoupon(coupon), user.getChatId());
                addCouponButton(sm, coupon, "Активировать купон", "couponPreview_");
                sendMessageCallback.execute(sm);
            });
        } else {
            InputStream imageStream = new URL(photoPath).openStream();
            InputFile inputFile = new InputFile(imageStream, "image.jpg");
            usersForCouponCities.forEach(user -> {
                sendMessageWithPhotoCallback.execute(inputFile, "", user.getChatId());
                var sm = botUtilityService.buildSendMessage(BotResponses.newCoupon(coupon), user.getChatId());
                addCouponButton(sm, coupon, "Активировать купон", "couponPreview_");
                sendMessageCallback.execute(sm);
            });
        }
    }

    private void addCouponToUser(final Coupon coupon, final User user) {
        user.getCoupons().add(coupon);
        userService.saveUser(user);
    }

    public Coupon getNewCoupon(final List<String> splitStringsFromAdminMessage) {
        List<String> trimmedStrings = splitStringsFromAdminMessage.stream().map(String::trim).toList();

        String type = trimmedStrings.get(1);

        String[] splitDates = trimmedStrings.get(2).split("-");
        LocalDateTime startDate = LocalDateTime.of(
                2000 + Integer.parseInt(splitDates[0].substring(4)),
                Integer.parseInt(splitDates[0].substring(2, 4)),
                Integer.parseInt(splitDates[0].substring(0, 2)),
                0, 0);
        LocalDateTime endDate = LocalDateTime.of(
                2000 + Integer.parseInt(splitDates[splitDates.length - 1].substring(4)),
                Integer.parseInt(splitDates[splitDates.length - 1].substring(2, 4)),
                Integer.parseInt(splitDates[splitDates.length - 1].substring(0, 2)),
                23, 59);

        int quantity = Integer.parseInt(trimmedStrings.get(3));

        List<Pharmacy> pharmacies = new ArrayList<>();
        if (trimmedStrings.get(4).equals("all")) {
            pharmacies.addAll(pharmacyService.findAll());
        } else {
            String[] pharmacyIds = trimmedStrings.get(4).split(",");
            for (String id : pharmacyIds) {
                pharmacies.add(pharmacyService.findById(Long.parseLong(id)));
            }
        }

        String name = trimmedStrings.get(5);
        String text = trimmedStrings.get(6);
        String news = trimmedStrings.get(7);

        Coupon coupon = new Coupon(type, startDate, endDate, quantity, 0, pharmacies, name, text, news);

        if (couponIsActive(coupon)) {
            return coupon;
        }

        sendMessageCallback.execute(botUtilityService.buildSendMessage("Формат купона неверен!", adminId));
        throw new RuntimeException("Coupon is not valid!");
    }

    public boolean couponIsActive(final Coupon coupon) {
        LocalDateTime zonedDateTime = LocalDateTime.now(ZoneId.of("Europe/Moscow"));
        if (coupon.getStartDate() != null && coupon.getEndDate() != null) {
            return zonedDateTime.isAfter(coupon.getStartDate()) && zonedDateTime.isBefore(coupon.getEndDate()) && coupon.getDailyQuantity() - coupon.getQuantityUsed() > 0;
        }
        return coupon.getDailyQuantity() - coupon.getQuantityUsed() > 0;
    }

    public void deleteExpiredCouponsAndReset() {
        List<Coupon> coupons = couponRepository.findAll();

        LocalDateTime zonedDateTime = LocalDateTime.now(ZoneId.of("Europe/Moscow"));
        List<Coupon> couponsToDelete = coupons.stream().filter(coupon -> coupon.getEndDate() != null && coupon.getEndDate().isBefore(zonedDateTime)).toList();
        couponsToDelete.forEach(coupon -> coupon.getUsers().forEach(user -> {
            user.getCoupons().remove(coupon);
            userService.saveUser(user);
        }));
        couponRepository.deleteAll(couponsToDelete);

        coupons = couponRepository.findAll();
        coupons.forEach(coupon -> coupon.setQuantityUsed(0));
        couponRepository.saveAll(coupons);
    }

    private Coupon increaseQuantityUsed(final Coupon coupon) {
        coupon.setQuantityUsed(coupon.getQuantityUsed() + 1);
        return couponRepository.save(coupon);
    }

    private String getCouponNumber(final Coupon coupon) {
        int number = coupon.getQuantityUsed();
        if (number < 10)
            return "000" + number;
        if (number < 100)
            return "00" + number;
        if (number < 1000)
            return "0" + number;
        return String.valueOf(number);
    }

    public InputFile getBarcodeInputFile(final String barcode) {
        byte[] imageBytes = restTemplate.getForObject("https://barcodeapi.org/api/Code39/" + barcode, byte[].class);
        assert imageBytes != null;
        InputStream imageStream = new ByteArrayInputStream(imageBytes);
        return new InputFile(imageStream, "image.png");
    }

    public String getTimeSign() {
        LocalDateTime zonedDateTime = LocalDateTime.now(ZoneId.of("Europe/Moscow"));
        return UtilityHelper.convertIntToTimeInt(zonedDateTime.getDayOfMonth()) + "." + UtilityHelper.convertIntToTimeInt(zonedDateTime.getMonthValue()) + "." + zonedDateTime.getYear()
                + " " + UtilityHelper.convertIntToTimeInt(zonedDateTime.getHour()) + ":" + UtilityHelper.convertIntToTimeInt(zonedDateTime.getMinute()) + ":" + UtilityHelper.convertIntToTimeInt(zonedDateTime.getSecond());
    }
}
