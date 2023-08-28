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
import org.telegram.telegrambots.meta.api.objects.InputFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

@Service
public class CouponService {

    public static final Map<MessageIdInChat, UserCouponRecord> userCoupons = new HashMap<>();

    @Value("${hello-coupon.barcode}")
    private String helloCouponBarcode;
    @Value("${coupon.duration-minutes}")
    private int couponDurationInMinutes;
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

    @PostConstruct
    public void initCallbacks() {
        this.sendMessageCallback = ledikomBot.getSendMessageCallback();
        this.sendMessageWithPhotoCallback = ledikomBot.getSendMessageWithPhotoCallback();
    }

    public Coupon findActiveCouponForUser(final User user, final String couponCommand) {
        int couponId = Integer.parseInt(couponCommand.split("_")[1]);
        return user.getCoupons().stream()
                .filter(coupon -> coupon.getId() == couponId)
                .filter(this::couponIsActive)
                .findFirst()
                .orElseThrow(() -> {
                    sendMessageCallback.execute(botUtilityService.buildSendMessage("Купон не найден / завершен / использован", user.getChatId()));
                    return new RuntimeException("Купон " + couponId + " не найден / завершен / использован для " + user.getChatId());
                });
    }

    public Coupon getHelloCoupon() {
        return couponRepository.findByBarcode(helloCouponBarcode).orElseThrow(() -> new RuntimeException("Hello coupon not found by barcode: " + helloCouponBarcode));
    }

    public User addAllActiveCouponsToUserByCity(final User user) {
        List<Coupon> coupons = couponRepository.findAll().stream()
                .filter(coupon -> coupon.getPharmacies().stream()
                        .anyMatch(pharmacy -> pharmacy.getCity() == user.getCity()))
                .toList();
        user.getCoupons().addAll(coupons);
        return user;
    }

    public void addHelloCouponToUser(final User user) {
        addCouponToUser(couponRepository.findByBarcode(helloCouponBarcode).orElseThrow(() -> new RuntimeException("Hello coupon not found by barcode: " + helloCouponBarcode)), user);
    }

    public void addCouponToMap(final MessageIdInChat messageIdInChat, final String couponText) {
        long expiryTimestamp = System.currentTimeMillis() + couponDurationInMinutes * 60 * 1000L;
        userCoupons.put(messageIdInChat, new UserCouponRecord(expiryTimestamp, couponText));
    }

    public void createAndSendNewCoupon(final String photoPath, final List<String> splitStringsFromAdminMessage) throws IOException {
        Coupon coupon = getNewCoupon(splitStringsFromAdminMessage);
        couponRepository.save(coupon);

        List<User> usersForCouponCities = userService.getAllUsersForCouponCities(coupon.getPharmacies());
        usersForCouponCities.forEach(user -> addCouponToUser(coupon, user));

        if (photoPath != null) {
            InputStream imageStream = new URL(photoPath).openStream();
            InputFile inputFile = new InputFile(imageStream, "image.jpg");
            usersForCouponCities.forEach(user -> sendMessageWithPhotoCallback.execute(inputFile, "", user.getChatId()));
        }
        usersForCouponCities.forEach(user -> {
            var sm = botUtilityService.buildSendMessage(BotResponses.newCoupon(coupon), user.getChatId());
            botUtilityService.addCouponButton(sm, coupon, "Активировать купон", "couponPreview_");
            sendMessageCallback.execute(sm);
        });
    }

    private void addCouponToUser(final Coupon coupon, final User user) {
        user.getCoupons().add(coupon);
        userService.saveUser(user);
    }

    // TODO: add regex checks and split on methods
    public Coupon getNewCoupon(final List<String> splitStringsFromAdminMessage) {

        String barcode = splitStringsFromAdminMessage.get(1);

        byte[] barcodeImageByteArray;
        try {
            barcodeImageByteArray = restTemplate.getForObject("https://barcodeapi.org/api/EAN13/" + barcode, byte[].class);
        } catch (RuntimeException e) {
            e.printStackTrace();
            String noBarcodeImagePath = "/no-barcode.jpg";
            try (InputStream inputStream = CouponService.class.getResourceAsStream(noBarcodeImagePath)) {
                if (inputStream == null) {
                    throw new IOException("Image file not found: " + noBarcodeImagePath);
                }
                barcodeImageByteArray = inputStream.readAllBytes();
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }

        String datesArgument = splitStringsFromAdminMessage.get(2);
        LocalDateTime startDate, endDate;
        if (datesArgument.isBlank()) {
            startDate = null;
            endDate = null;
        } else {
            String[] splitDates = datesArgument.split("-");
            startDate = LocalDateTime.of(
                    2000 + Integer.parseInt(splitDates[0].substring(4)),
                    Integer.parseInt(splitDates[0].substring(2, 4)),
                    Integer.parseInt(splitDates[0].substring(0, 2)),
                    0, 0);
            endDate = LocalDateTime.of(
                    2000 + Integer.parseInt(splitDates[splitDates.length - 1].substring(4)),
                    Integer.parseInt(splitDates[splitDates.length - 1].substring(2, 4)),
                    Integer.parseInt(splitDates[splitDates.length - 1].substring(0, 2)),
                    23, 59);
        }

        List<Pharmacy> pharmacies = new ArrayList<>();
        String pharmaciesArgument = splitStringsFromAdminMessage.get(3);
        if (pharmaciesArgument.isBlank()) {
            pharmacies.addAll(pharmacyService.findAll());
        } else {
            String[] pharmacyIds = pharmaciesArgument.split(",");
            for (String id : pharmacyIds) {
                pharmacies.add(pharmacyService.findById(Long.parseLong(id)));
            }
        }

        String name = splitStringsFromAdminMessage.get(4);
        String text = splitStringsFromAdminMessage.get(5);
        String news = splitStringsFromAdminMessage.get(6);

        Coupon coupon = new Coupon(barcode, barcodeImageByteArray, startDate, endDate, pharmacies, name, text, news);

        if (!couponIsActive(coupon)) {
            sendMessageCallback.execute(botUtilityService.buildSendMessage("Купон неактивен! Проверьте даты действия!", adminId));
            throw new RuntimeException("Купон неактивен! Проверьте даты действия!");
        }

        return coupon;
    }

    public boolean couponIsActive(final Coupon coupon) {
        LocalDateTime zonedDateTime = LocalDateTime.now(ZoneId.of("Europe/Moscow"));
        if (coupon.getStartDate() == null && coupon.getEndDate() == null) {
            return true;
        }
        if (coupon.getStartDate() != null && coupon.getEndDate() != null) {
            return zonedDateTime.isAfter(coupon.getStartDate()) && zonedDateTime.isBefore(coupon.getEndDate());
        }
        return false;
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
    }

    public String getTimeSign() {
        LocalDateTime zonedDateTime = LocalDateTime.now(ZoneId.of("Europe/Moscow"));
        return UtilityHelper.convertIntToTimeInt(zonedDateTime.getDayOfMonth()) + "." + UtilityHelper.convertIntToTimeInt(zonedDateTime.getMonthValue()) + "." + zonedDateTime.getYear()
                + " " + UtilityHelper.convertIntToTimeInt(zonedDateTime.getHour()) + ":" + UtilityHelper.convertIntToTimeInt(zonedDateTime.getMinute()) + ":" + UtilityHelper.convertIntToTimeInt(zonedDateTime.getSecond());
    }
}
