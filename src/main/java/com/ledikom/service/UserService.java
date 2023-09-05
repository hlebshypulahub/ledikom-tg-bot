package com.ledikom.service;

import com.ledikom.bot.LedikomBot;
import com.ledikom.callback.SendMessageCallback;
import com.ledikom.callback.SendMessageWithPhotoCallback;
import com.ledikom.model.*;
import com.ledikom.repository.UserRepository;
import com.ledikom.utils.BotResponses;
import com.ledikom.utils.City;
import com.ledikom.utils.UserResponseState;
import jakarta.annotation.PostConstruct;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.polls.Poll;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

@Service
public class UserService {
    private static final int INIT_REFERRAL_COUNT = 0;
    private static final boolean INIT_RECEIVE_NEWS = true;
    private static final UserResponseState INIT_RESPONSE_STATE = UserResponseState.NONE;

    private static final Logger LOGGER = LoggerFactory.getLogger(UserService.class);

    @Value("${bot.username}")
    private String botUsername;

    private final UserRepository userRepository;
    private final CouponService couponService;
    private final PollService pollService;
    private final BotUtilityService botUtilityService;
    private final PharmacyService pharmacyService;
    private final LedikomBot ledikomBot;

    private SendMessageCallback sendMessageCallback;
    private SendMessageWithPhotoCallback sendMessageWithPhotoCallback;

    public UserService(final UserRepository userRepository, @Lazy final CouponService couponService, final PollService pollService, final BotUtilityService botUtilityService, final PharmacyService pharmacyService, @Lazy final LedikomBot ledikomBot) {
        this.userRepository = userRepository;
        this.couponService = couponService;
        this.pollService = pollService;
        this.botUtilityService = botUtilityService;
        this.pharmacyService = pharmacyService;
        this.ledikomBot = ledikomBot;
    }

    @PostConstruct
    public void initCallbacks() {
        this.sendMessageCallback = ledikomBot.getSendMessageCallback();
        this.sendMessageWithPhotoCallback = ledikomBot.getSendMessageWithPhotoCallback();
    }

    public List<User> findAllUsers() {
        return userRepository.findAll();
    }

    public void addNewUser(final Long chatId) {
        LOGGER.info("Saving user");
        User user = new User(chatId, INIT_REFERRAL_COUNT, INIT_RECEIVE_NEWS, INIT_RESPONSE_STATE);
        User savedUser = userRepository.save(user);
        LOGGER.info("Saved user: {}", savedUser);
        couponService.addHelloCouponToUser(user);
    }

    public void saveUser(final User user) {
        userRepository.save(user);
    }

    public User findByChatId(final Long chatId) {
        return userRepository.findByChatId(chatId).orElseThrow(() -> new RuntimeException("User not found by id " + chatId));
    }

    public void processPoll(final Poll telegramPoll) {
        // check if not a re-vote
        if (telegramPoll.getTotalVoterCount() == 1) {
            com.ledikom.model.Poll pollToUpdate = pollService.findByQuestion(telegramPoll.getQuestion());

            List<PollOption> pollOptionList = IntStream.range(0, telegramPoll.getOptions().size())
                    .mapToObj(index -> new PollOption(
                            pollToUpdate.getOptions().get(index).getText(),
                            pollToUpdate.getOptions().get(index).getVoterCount() + telegramPoll.getOptions().get(index).getVoterCount()))
                    .toList();
            pollToUpdate.setOptions(pollOptionList);
            pollToUpdate.setTotalVoterCount(pollToUpdate.getTotalVoterCount() + 1);
            pollToUpdate.setLastVoteTimestamp(LocalDateTime.now());

            pollService.savePoll(pollToUpdate);

            BotService.eventCollector.incrementPoll();
        }
    }

    public void processStatefulUserResponse(final String text, final Long chatId) {
        User user = findByChatId(chatId);
        if (user.getResponseState() == UserResponseState.SENDING_NOTE) {
            user.setNote(text);
            user.setResponseState(UserResponseState.NONE);
            saveUser(user);
            sendMessageCallback.execute(botUtilityService.buildSendMessage(BotResponses.noteAdded(), chatId));
            BotService.eventCollector.incrementNote();
        } else if (user.getResponseState() == UserResponseState.SENDING_DATE) {
            try {
                String[] splitDateString = text.trim().split("\\.");
                if (splitDateString.length != 2) {
                    throw new RuntimeException();
                }
                var day = Integer.parseInt(splitDateString[0]);
                var month = Integer.parseInt(splitDateString[1]);
                LocalDateTime specialDate = LocalDateTime.of(2000, month, day, 0, 0);
                user.setSpecialDate(specialDate);
                user.setResponseState(UserResponseState.NONE);
                saveUser(user);
                sendMessageCallback.execute(botUtilityService.buildSendMessage(BotResponses.yourSpecialDate(specialDate), chatId));
                BotService.eventCollector.incrementDate();
            } catch (RuntimeException e) {
                sendMessageCallback.execute(botUtilityService.buildSendMessage("Неверный формат даты, введите сообщение в цифровом формате:\n\nдень.месяц", chatId));
                throw new RuntimeException("Invalid special date format: " + text);
            }
        } else {
            sendMessageCallback.execute(botUtilityService.buildSendMessage("Нет такой команды!", chatId));
            throw new RuntimeException("Invalid user response state: " + user.getResponseState());
        }
    }

    public void markCouponAsUsedForUser(final User user, final Coupon coupon) {
        if (user.getCoupons().remove(coupon)) {
            userRepository.save(user);
        }
    }

    public void addNewRefUser(final long chatIdFromRefLink, final long chatId) {
        final boolean selfLinkOrUserExists = chatIdFromRefLink == chatId || userExistsByChatId(chatId);
        if (!selfLinkOrUserExists) {
            User user = findByChatId(chatIdFromRefLink);
            user.setReferralCount(user.getReferralCount() + 1);
            sendMessageCallback.execute(botUtilityService.buildSendMessage(BotResponses.referralMessage(getRefLink(chatIdFromRefLink), user.getReferralCount()), chatIdFromRefLink));
            couponService.addRefCouponToUser(user);
            userRepository.save(user);
            BotService.eventCollector.incrementRefLink();
        }
    }

    public boolean userExistsByChatId(final long chatId) {
        if (userRepository.findByChatId(chatId).isPresent()) {
            return true;
        }
        LOGGER.error("User not exists by chatId: {}", chatId);
        return false;
    }

    public List<SendMessage> processNoteRequestAndBuildSendMessageList(final long chatId) {
        User user = findByChatId(chatId);
        user.setResponseState(UserResponseState.SENDING_NOTE);
        saveUser(user);

        if (user.getNote() != null && !user.getNote().isBlank()) {
            SendMessage smNote = botUtilityService.buildSendMessage(user.getNote(), chatId);
            SendMessage smInfo = botUtilityService.buildSendMessage(BotResponses.editNote(), chatId);
            return List.of(smInfo, smNote);
        }

        SendMessage sm = botUtilityService.buildSendMessage(BotResponses.addNote(), chatId);
        return List.of(sm);
    }

    public boolean userIsInActiveState(final Long chatId) {
        return findByChatId(chatId).getResponseState() != UserResponseState.NONE;
    }

    @Transactional
    public void setCityToUserAndAddCoupons(final String cityName, final Long chatId) {
        User user = findByChatId(chatId);
        user.setCity(City.valueOf(cityName));

        List<Coupon> activeCouponsForUser = couponService.findAllTempActiveCouponsByCity(user.getCity());

        couponService.clearUserCityCoupons(user);

        if (activeCouponsForUser.size() > 0) {
            user.getCoupons().addAll(activeCouponsForUser);
            userRepository.save(user);

            sendMessageCallback.execute(botUtilityService.buildSendMessage(BotResponses.cityAddedAndNewCouponsGot(cityName), chatId));
            sendAllCouponsList(user.getChatId());
        } else {
            sendMessageCallback.execute(botUtilityService.buildSendMessage(BotResponses.cityAdded(cityName), chatId));
        }

        BotService.eventCollector.incrementCity();
    }

    public List<User> findAllUsersToSendNews() {
        return findAllUsers().stream().filter(User::getReceiveNews).toList();
    }

    public List<User> filterUsersToSendNews(final List<User> users) {
        return users.stream().filter(User::getReceiveNews).toList();
    }

    public List<User> findAllUsersByPharmaciesCities(final Set<Pharmacy> pharmacies) {
        return findAllUsers().stream().filter(user -> user.getCity() == null || pharmacies.stream().map(Pharmacy::getCity).toList().contains(user.getCity())).toList();
    }

    public void sendNewsToUsers(final NewsFromAdmin newsFromAdmin) throws IOException {
        LOGGER.info("Sending news to users");

        List<User> usersToSendNews = findAllUsersToSendNews();

        if (newsFromAdmin.getPhotoPath() == null) {
            usersToSendNews.forEach(user -> sendMessageCallback.execute(botUtilityService.buildSendMessage(newsFromAdmin.getText(), user.getChatId())));
        } else {
            InputStream imageStream = new URL(newsFromAdmin.getPhotoPath()).openStream();
            InputFile inputFile = new InputFile(imageStream, "image.jpg");
            usersToSendNews.forEach(user -> sendMessageWithPhotoCallback.execute(inputFile, newsFromAdmin.getText(), user.getChatId()));
        }
    }

    public void sendPromotionToUsers(final PromotionFromAdmin promotionFromAdmin) throws IOException {
        LOGGER.info("Sending promotion to users");

        List<User> usersToSendPromotion = filterUsersToSendNews(findAllUsersByPharmaciesCities(new HashSet<>(promotionFromAdmin.getPharmacies())));

        if (promotionFromAdmin.getPhotoPath() != null) {
            InputStream imageStream = new URL(promotionFromAdmin.getPhotoPath()).openStream();
            InputFile inputFile = new InputFile(imageStream, "image.jpg");
            usersToSendPromotion.forEach(user -> sendMessageWithPhotoCallback.execute(inputFile, "", user.getChatId()));
        }

        usersToSendPromotion.forEach(user -> {
            boolean inAllPharmacies = promotionFromAdmin.getPharmacies().size() == pharmacyService.findAll().size();
            var sm = botUtilityService.buildSendMessage(BotResponses.promotionText(promotionFromAdmin, inAllPharmacies), user.getChatId());
            botUtilityService.addPromotionAcceptButton(sm);
            sendMessageCallback.execute(sm);
        });
    }

    public void sendAllCouponsList(final Long chatId) {
        User user = findByChatId(chatId);
        Set<Coupon> userCoupons = user.getCoupons();

        SendMessage sm;
        if (userCoupons.isEmpty()) {
            sm = botUtilityService.buildSendMessage(BotResponses.noActiveCouponsMessage(), chatId);
        } else {
            sm = botUtilityService.buildSendMessage(BotResponses.listOfCouponsMessage(), chatId);
            sm.setReplyMarkup(botUtilityService.createListOfCoupons(userCoupons));
        }
        sendMessageCallback.execute(sm);
    }

    public void sendPollToUsers(final Poll poll) {
        LOGGER.info("Sending poll to users");

        List<User> usersToSendNews = findAllUsersToSendNews();
        usersToSendNews.forEach(user -> sendMessageCallback.execute(botUtilityService.buildSendPoll(poll, user.getChatId())));
    }

    public void sendReferralLinkForUser(final Long chatId) {
        sendMessageCallback.execute(botUtilityService.buildSendMessage(BotResponses.referralMessage(getRefLink(chatId), findByChatId(chatId).getReferralCount()), chatId));
    }

    private String getRefLink(final Long chatId) {
        return "https://t.me/" + botUsername + "?start=" + chatId;
    }

    public void triggerReceiveNewsMessage(final Long chatId) {
        User user = findByChatId(chatId);
        user.setReceiveNews(!user.getReceiveNews());
        if (user.getReceiveNews()) {
            BotService.eventCollector.decrementNewsDisabled();
        } else {
            BotService.eventCollector.incrementNewsDisabled();
        }
        saveUser(user);
        sendMessageCallback.execute(botUtilityService.buildSendMessage(BotResponses.triggerReceiveNewsMessage(user), chatId));
    }

    public void sendNoteAndSetUserResponseState(final long chatId) {
        List<SendMessage> sendMessageList = processNoteRequestAndBuildSendMessageList(chatId);
        sendMessageList.forEach(sm -> sendMessageCallback.execute(sm));
    }

    public void sendDateAndSetUserResponseState(final long chatId) {
        User user = findByChatId(chatId);
        SendMessage sm;
        if (user.getSpecialDate() == null) {
            user.setResponseState(UserResponseState.SENDING_DATE);
            saveUser(user);
            sm = botUtilityService.buildSendMessage(BotResponses.addSpecialDate(), chatId);
        } else {
            sm = botUtilityService.buildSendMessage(BotResponses.yourSpecialDate(user.getSpecialDate()), chatId);
        }
        sendMessageCallback.execute(sm);
    }
}
