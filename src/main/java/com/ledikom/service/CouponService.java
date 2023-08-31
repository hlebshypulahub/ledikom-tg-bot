package com.ledikom.service;

import com.ledikom.bot.LedikomBot;
import com.ledikom.callback.SendMessageCallback;
import com.ledikom.callback.SendMessageWithPhotoCallback;
import com.ledikom.model.*;
import com.ledikom.repository.CouponRepository;
import com.ledikom.utils.BotResponses;
import com.ledikom.utils.City;
import jakarta.annotation.PostConstruct;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.telegram.telegrambots.meta.api.objects.InputFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class CouponService {

    public static final Map<MessageIdInChat, UserCouponRecord> userCoupons = new HashMap<>();

    @Value("${hello-coupon.barcode}")
    private String helloCouponBarcode;
    @Value("${date-coupon.barcode}")
    private String dateCouponBarcode;
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
    public void initCallbacks() throws IOException {
        this.sendMessageCallback = ledikomBot.getSendMessageCallback();
        this.sendMessageWithPhotoCallback = ledikomBot.getSendMessageWithPhotoCallback();
        saveStaleCoupons();
    }

    private void saveStaleCoupons() {
        if (couponRepository.findByBarcode(helloCouponBarcode).isEmpty()) {
            Coupon coupon = getNewValidCoupon(List.of("coupon", helloCouponBarcode, "", "", "Приветственный купон -5%",
                    "Здоровье – важнейшая ценность! С этим купоном вы получаете 5% скидку на любой лекарственный препарат из нашего ассортимента!", ""));
            couponRepository.save(coupon);
        }
        if (couponRepository.findByBarcode(dateCouponBarcode).isEmpty()) {
            Coupon coupon = getNewValidCoupon(List.of("coupon", dateCouponBarcode, "", "", "Особенная дата",
                    "Здоровье – важнейшая ценность! С этим купоном вы получаете 5% скидку на любой лекарственный препарат из нашего ассортимента!", ""));
            couponRepository.save(coupon);
        }
    }

    public Coupon findCouponForUser(final User user, final String couponCommand) {
        int couponId = Integer.parseInt(couponCommand.split("_")[1]);
        return user.getCoupons().stream()
                .filter(coupon -> coupon.getId() == couponId)
                .filter(this::couponCanBeUsedNow)
                .findFirst()
                .orElseThrow(() -> {
                    sendMessageCallback.execute(botUtilityService.buildSendMessage("Купон не найден / завершен / использован", user.getChatId()));
                    return new RuntimeException("Купон " + couponId + " не найден / завершен / использован для " + user.getChatId());
                });
    }

    public Coupon getHelloCoupon() {
        return couponRepository.findByBarcode(helloCouponBarcode).orElseThrow(() -> new RuntimeException("Hello coupon not found by barcode: " + helloCouponBarcode));
    }

    public List<Coupon> findAllTempActiveCouponsForUserByCity(final City city) {
        return couponRepository.findAll().stream()
                .filter(this::couponIsTempAndActive)
                .filter(coupon -> coupon.getPharmacies().stream()
                        .anyMatch(pharmacy -> pharmacy.getCity() == city))
                .toList();
    }

    public void addHelloCouponToUser(final User user) {
        addCouponToUser(couponRepository.findByBarcode(helloCouponBarcode).orElseThrow(() -> new RuntimeException("Hello coupon not found by barcode: " + helloCouponBarcode)), user);
    }

    public void addCouponToMap(final MessageIdInChat messageIdInChat, final String couponText) {
        long expiryTimestamp = System.currentTimeMillis() + couponDurationInMinutes * 60 * 1000L;
        userCoupons.put(messageIdInChat, new UserCouponRecord(expiryTimestamp, couponText));
    }

    public void createAndSendNewCoupon(final String photoPath, final List<String> splitStringsFromAdminMessage) throws IOException {
        Coupon coupon = getNewValidCoupon(splitStringsFromAdminMessage);
        couponRepository.save(coupon);

        if (couponIsTempAndActive(coupon)) {
            List<User> usersForCouponCities = userService.findAllUsersByPharmaciesCities(coupon.getPharmacies());
            usersForCouponCities.forEach(user -> addCouponToUser(coupon, user));

            sendCouponNewsToUsers(coupon, userService.filterUsersToSendNews(usersForCouponCities), photoPath);

            sendMessageCallback.execute(botUtilityService.buildSendMessage("Купон добавлен, рассылка произведена.", adminId));
        } else {

            sendMessageCallback.execute(botUtilityService.buildSendMessage("Купон добавлен, рассылка будет произведена в первый день действия купона: "
                    + (coupon.getStartDate() != null ? coupon.getStartDate().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) : "нет даты"), adminId));
        }
    }

    private void sendCouponNewsToUsers(final Coupon coupon, final List<User> users, final String photoPath) throws IOException {
        if (photoPath != null) {
            InputStream imageStream = new URL(photoPath).openStream();
            InputFile inputFile = new InputFile(imageStream, "image.jpg");
            users.forEach(user -> sendMessageWithPhotoCallback.execute(inputFile, "", user.getChatId()));
        }
        sendCouponNewsToUsers(coupon, users);
    }

    private void sendCouponNewsToUsers(final Coupon coupon, final List<User> users) {
        users.forEach(user -> {
            var sm = botUtilityService.buildSendMessage(BotResponses.newCoupon(coupon), user.getChatId());
            botUtilityService.addPreviewCouponButton(sm, coupon, "Активировать купон");
            sendMessageCallback.execute(sm);
        });
    }

    public void addDateCouponToUsers() {
        Coupon coupon = couponRepository.findByBarcode(dateCouponBarcode).orElseThrow(() -> new RuntimeException("Date coupon not exist by barcode " + dateCouponBarcode));

        userService.findAllUsers().stream()
                .filter(user ->
                        user.getSpecialDate() != null
                                && user.getSpecialDate().getDayOfMonth() == getZonedDateTimeNow().getDayOfMonth()
                                && user.getSpecialDate().getMonth() == getZonedDateTimeNow().getMonth())
                .toList()
                .forEach(user -> {
                    addCouponToUser(coupon, user);
                    var sm = botUtilityService.buildSendMessage(BotResponses.specialDay(), user.getChatId());
                    botUtilityService.addPreviewCouponButton(sm, coupon, "Активировать");
                    sendMessageCallback.execute(sm);
                });
    }

    public void addCouponsToUsersOnFirstActiveDay() {
        List<User> users = userService.findAllUsers();

        List<Coupon> couponsWithFirstActiveDay = couponRepository.findAll().stream().filter(coupon -> datesEqual(coupon.getStartDate(), getZonedDateTimeNow())).toList();

        for (Coupon coupon : couponsWithFirstActiveDay) {
            List<User> usersToGetNewCoupon = new ArrayList<>();
            users.forEach(user -> {
                if (coupon.getPharmacies().stream().anyMatch(pharmacy -> user.getCity() == null || pharmacy.getCity() == user.getCity())) {
                    usersToGetNewCoupon.add(user);
                    addCouponToUser(coupon, user);
                }
            });
            sendCouponNewsToUsers(coupon, userService.filterUsersToSendNews(usersToGetNewCoupon));
        }
    }

    private boolean datesEqual(final LocalDateTime date1, final LocalDateTime date2) {
        return date1.getYear() == date2.getYear() &&
                date1.getMonth() == date2.getMonth() &&
                date1.getDayOfMonth() == date2.getDayOfMonth();
    }

    private void addCouponToUser(final Coupon coupon, final User user) {
        user.getCoupons().add(coupon);
        userService.saveUser(user);
    }

    // TODO: add regex checks and split on methods
    private Coupon getNewValidCoupon(final List<String> splitStringsFromAdminMessage) {

        String barcode = splitStringsFromAdminMessage.get(1);

        if (couponRepository.findByBarcode(barcode).isPresent()) {
            throw new RuntimeException("Coupon already exists with barcode: " + barcode);
        }

        byte[] barcodeImageByteArray;
        try {
            barcodeImageByteArray = restTemplate.getForObject("https://barcodeapi.org/api/EAN13/" + barcode, byte[].class);
        } catch (RuntimeException e) {
            e.printStackTrace();
            String noBarcodeImagePath = "/no-barcode.png";
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
            if (barcode.equals(helloCouponBarcode) || barcode.equals(dateCouponBarcode)) {
                startDate = null;
                endDate = null;
            } else {
                sendMessageCallback.execute(botUtilityService.buildSendMessage("Купон неактивен! Проверьте даты действия!", adminId));
                throw new RuntimeException("Купон неактивен! Проверьте даты действия!");
            }
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

        List<Pharmacy> pharmacies = pharmacyService.getPharmaciesFromIdsString(splitStringsFromAdminMessage.get(3));

        String name = splitStringsFromAdminMessage.get(4);
        String text = splitStringsFromAdminMessage.get(5);
        String news = splitStringsFromAdminMessage.get(6);

        return new Coupon(barcode, barcodeImageByteArray, startDate, endDate, pharmacies, name, text, news);
    }

    private boolean couponIsTempAndActive(final Coupon coupon) {
        if (coupon.getStartDate() != null && coupon.getEndDate() != null) {
            return getZonedDateTimeNow().isAfter(coupon.getStartDate()) && getZonedDateTimeNow().isBefore(coupon.getEndDate());
        }
        return false;
    }

    private boolean couponIsStale(final Coupon coupon) {
        return coupon.getStartDate() == null && coupon.getEndDate() == null;
    }

    public boolean couponCanBeUsedNow(final Coupon coupon) {
        return couponIsStale(coupon) || couponIsTempAndActive(coupon);
    }

    private LocalDateTime getZonedDateTimeNow() {
        return LocalDateTime.now(ZoneId.of("Europe/Moscow"));
    }

    public void deleteExpiredCoupons() {
        List<Coupon> coupons = couponRepository.findAll();

        List<Coupon> couponsToDelete = coupons.stream().filter(coupon -> coupon.getEndDate() != null && coupon.getEndDate().isBefore(getZonedDateTimeNow())).toList();
        couponsToDelete.forEach(coupon -> coupon.getUsers().forEach(user -> {
            user.getCoupons().remove(coupon);
            userService.saveUser(user);
        }));
        couponRepository.deleteAll(couponsToDelete);
    }

    @Transactional
    public void clearUserCityCoupons(final User user) {
        List<Coupon> couponsToDelete = user.getCoupons().stream().filter(coupon -> !couponIsStale(coupon)).toList();
        couponsToDelete.forEach(coupon -> {
            user.getCoupons().remove(coupon);
            userService.saveUser(user);
        });
    }

    public String getTimeSign() {
        return getZonedDateTimeNow().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"));
    }
}
