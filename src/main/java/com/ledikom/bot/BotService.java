package com.ledikom.bot;

import com.ledikom.callback.*;
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
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.File;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.PhotoSize;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

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
    @Value("${coupon.duration-in-minutes}")
    private int couponDurationInMinutes;
    @Value("${bot.username}")
    private String botUsername;
    @Value("${bot.token}")
    private String botToken;

    private final UserService userService;
    private final CouponService couponService;

    public BotService(final UserService userService, final CouponService couponService) {
        this.userService = userService;
        this.couponService = couponService;
    }

    public void addUserAndGenerateHelloMessage(final SendMessageCallback sendMessageCallback, final long chatId) {
        if (!userService.userExistsByChatId(chatId)) {
            userService.addNewUser(chatId);
            var sm = buildSendMessage(BotResponses.startMessage(), chatId);
            Coupon coupon = couponService.findByName(helloCouponName);
            addCouponButton(sm, coupon, "Активировать приветственный купон", "couponPreview_");
            sendMessageCallback.execute(sm);
        }
    }

    public void generateCouponAcceptMessageIfNotUsed(final SendMessageCallback sendMessageCallback,
                                                     final String couponCommand, final long chatId) {
        User user = userService.findByChatId(chatId);
        Coupon coupon = couponService.findCouponForUser(user, couponCommand);

        if (coupon != null) {
            var sm = buildSendMessage(BotResponses.couponAcceptMessage(couponDurationInMinutes), chatId);
            addCouponButton(sm, coupon, "Активировать", "couponAccept_");
            sendMessageCallback.execute(sm);
        } else {
            var sm = buildSendMessage(BotResponses.couponUsedMessage(), chatId);
            sendMessageCallback.execute(sm);
        }
    }

    public void generateCouponIfNotUsed(final SendMessageCallback sendMessageCallback,
                                        final SendCouponByChatIdCallback sendCouponByChatIdCallback,
                                        final String couponCommand, final Long chatId) {
        User user = userService.findByChatId(chatId);
        Coupon coupon = couponService.findCouponForUser(user, couponCommand);

        if (coupon == null) {
            var sm = buildSendMessage(BotResponses.couponUsedMessage(), chatId);
            sendMessageCallback.execute(sm);
        } else {
            String couponTextWithUniqueSign = generateSignedCoupon(coupon);
            var sm = buildSendMessage(getInitialCouponText(couponTextWithUniqueSign), chatId);
            UserCouponKey userCouponKey = sendCouponByChatIdCallback.execute(sm);
            addCouponToMap(userCouponKey, couponTextWithUniqueSign);
            userService.removeCouponFromUser(user, coupon);
        }
    }

    private String getInitialCouponText(final String couponTextWithUniqueSign) {
        return "Времени осталось: " + convertIntToTimeInt(couponDurationInMinutes) + ":00" +
                "\n\n" +
                couponTextWithUniqueSign;
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
        long expiryTimestamp = System.currentTimeMillis() + couponDurationInMinutes * 60 * 1000L;
        userCoupons.put(userCouponKey, new UserCouponRecord(expiryTimestamp, couponText));
    }

    public String updateCouponText(final UserCouponRecord userCouponRecord, final long timeLeftInSeconds) {
        return "Времени осталось: " + timeLeftInSeconds / 60 + ":" + (timeLeftInSeconds % 60 < 10 ? "0" + timeLeftInSeconds % 60 : timeLeftInSeconds % 60) +
                "\n\n" +
                userCouponRecord.getText();
    }

    public String generateSignedCoupon(final Coupon coupon) {
        ZoneId moscowZone = ZoneId.of("Europe/Moscow");
        LocalDateTime zonedDateTime = LocalDateTime.now(moscowZone).plusMinutes(couponDurationInMinutes);

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

    public void showAllCoupons(final SendMessageCallback sendMessageCallback, final Long chatId) {
        User user = userService.findByChatId(chatId);
        Set<Coupon> userCoupons = user.getCoupons();

        SendMessage sm;
        if (userCoupons.isEmpty()) {
            sm = buildSendMessage("У вас нету купонов", chatId);
        } else {
            sm = buildSendMessage("Ваши купоны:", chatId);
            sm.setReplyMarkup(createListOfCoupons(userCoupons));
        }
        sendMessageCallback.execute(sm);
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

    public void getReferralLinkForUser(final SendMessageCallback sendMessageCallback, final Long chatId) {
        String refLink = "https://t.me/" + botUsername + "?start=" + chatId;
        sendMessageCallback.execute(buildSendMessage(BotResponses.referralMessage(refLink, userService.findByChatId(chatId).getReferralCount()), chatId));
    }

    public void generateTriggerReceiveNewsMessage(final SendMessageCallback sendMessageCallback, final Long chatId) {
        User user = userService.findByChatId(chatId);
        user.setReceiveNews(!user.getReceiveNews());
        userService.saveUser(user);
        sendMessageCallback.execute(buildSendMessage("Подписка на рассылку новостей и акций " + (user.getReceiveNews() ? "включена." : "отключена."), chatId));
    }

    public void processAdminMessage(final SendMessageCallback sendMessageCallback,
                                    final SendMessageWithPhotoByChatIdCallback sendMessageWithPhotoByChatIdCallback,
                                    final String text, final String photoPathReceivedFromAdmin) {
        List<String> splitString = Arrays.stream(text.split("&")).map(String::trim).toList();
        if (splitString.get(0).equalsIgnoreCase("news")) {
            List<User> usersToSendNews = userService.getAllUsersToSendNews();
            if (photoPathReceivedFromAdmin == null || photoPathReceivedFromAdmin.isBlank()) {
                usersToSendNews.forEach(user -> sendMessageCallback.execute(SendMessage.builder()
                        .chatId(user.getChatId())
                        .text(splitString.get(1)).build()));
            } else {
                usersToSendNews.forEach(user -> sendMessageWithPhotoByChatIdCallback.execute(photoPathReceivedFromAdmin, splitString.get(1), user.getChatId()));
            }
        }
    }

    public String getPhotoFromUpdate(final Message msg, final GetFileFromBotCallback getFileFromBotCallback) {
        PhotoSize photo = msg.getPhoto().stream()
                .max(Comparator.comparingInt(PhotoSize::getWidth))
                .orElse(null);
        if (photo != null) {
            GetFile getFileRequest = new GetFile();
            getFileRequest.setFileId(photo.getFileId());
            try {
                File file = getFileFromBotCallback.execute(getFileRequest);
                return "https://api.telegram.org/file/bot" + botToken + "/" + file.getFilePath();
            } catch (TelegramApiException e) {
                throw new RuntimeException(e);
            }
        }
        return null;
    }

    private SendMessage buildSendMessage(String text, long chatId) {
        return SendMessage.builder()
                .chatId(chatId)
                .text(text).build();
    }
}
