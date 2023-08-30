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
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

@Service
public class UserService {
    private static final int INIT_REFERRAL_COUNT = 0;
    private static final boolean INIT_RECEIVE_NEWS = true;
    private static final UserResponseState INIT_RESPONSE_STATE = UserResponseState.NONE;

    @Value("${bot.username}")
    private String botUsername;

    private final UserRepository userRepository;
    private final CouponService couponService;
    private final PollService pollService;
    private final BotUtilityService botUtilityService;
    private final LedikomBot ledikomBot;

    private SendMessageCallback sendMessageCallback;
    private SendMessageWithPhotoCallback sendMessageWithPhotoCallback;

    public UserService(final UserRepository userRepository, @Lazy final CouponService couponService, final PollService pollService, final BotUtilityService botUtilityService, @Lazy final LedikomBot ledikomBot) {
        this.userRepository = userRepository;
        this.couponService = couponService;
        this.pollService = pollService;
        this.botUtilityService = botUtilityService;
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
        User user = new User(chatId, INIT_REFERRAL_COUNT, INIT_RECEIVE_NEWS, INIT_RESPONSE_STATE);
        userRepository.save(user);
        couponService.addHelloCouponToUser(user);
    }

    public void saveUser(final User user) {
        userRepository.save(user);
    }

    public User findByChatId(final Long chatId) {
        return userRepository.findByChatId(chatId).orElseThrow(() -> new RuntimeException("User not found with id " + chatId));
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
        }
    }

    public void processStatefulUserResponse(final String text, final Long chatId) {
        User user = findByChatId(chatId);
        if (user.getResponseState() == UserResponseState.SENDING_NOTE) {
            user.setNote(text);
            user.setResponseState(UserResponseState.NONE);
            saveUser(user);
            sendMessageCallback.execute(botUtilityService.buildSendMessage(BotResponses.noteAdded(), chatId));
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
            userRepository.save(user);
        }
    }

    public boolean userExistsByChatId(final long chatId) {
        return userRepository.findByChatId(chatId).isPresent();
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

        List<Coupon> activeCouponsForUser = couponService.findAllTempActiveCouponsForUserByCity(user.getCity());

        couponService.clearUserCityCoupons(user);

        if (activeCouponsForUser.size() > 0) {
            user.getCoupons().addAll(activeCouponsForUser);
            userRepository.save(user);

            sendMessageCallback.execute(botUtilityService.buildSendMessage(BotResponses.cityAddedAndNewCouponsGot(cityName), chatId));
            sendAllCouponsList(user.getChatId());
        } else {
            sendMessageCallback.execute(botUtilityService.buildSendMessage(BotResponses.cityAdded(cityName), chatId));
        }
    }

    public List<User> findAllUsersToSendNews() {
        return findAllUsers().stream().filter(User::getReceiveNews).toList();
    }

    public List<User> filterUsersToSendNews(final List<User> users) {
        return users.stream().filter(User::getReceiveNews).toList();
    }

    public List<User> findAllUsersToAddCouponByPharmacies(final Set<Pharmacy> pharmacies) {
        return findAllUsers().stream().filter(user -> user.getCity() == null || pharmacies.stream().map(Pharmacy::getCity).toList().contains(user.getCity())).toList();
    }

    public void sendNewsToUsers(final NewsFromAdmin newsFromAdmin) throws IOException {
        List<User> usersToSendNews = findAllUsersToSendNews();

        if (newsFromAdmin.getPhotoPath() == null) {
            usersToSendNews.forEach(user -> sendMessageCallback.execute(botUtilityService.buildSendMessage(newsFromAdmin.getNews(), user.getChatId())));
        } else {
            InputStream imageStream = new URL(newsFromAdmin.getPhotoPath()).openStream();
            InputFile inputFile = new InputFile(imageStream, "image.jpg");
            usersToSendNews.forEach(user -> sendMessageWithPhotoCallback.execute(inputFile, newsFromAdmin.getNews(), user.getChatId()));
        }
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
        List<User> usersToSendNews = findAllUsersToSendNews();
        usersToSendNews.forEach(user -> sendMessageCallback.execute(botUtilityService.buildSendPoll(poll, user.getChatId())));
    }

    public void sendReferralLinkForUser(final Long chatId) {
        String refLink = "https://t.me/" + botUsername + "?start=" + chatId;
        sendMessageCallback.execute(botUtilityService.buildSendMessage(BotResponses.referralMessage(refLink, findByChatId(chatId).getReferralCount()), chatId));
    }

    public void sendTriggerReceiveNewsMessage(final Long chatId) {
        User user = findByChatId(chatId);
        user.setReceiveNews(!user.getReceiveNews());
        saveUser(user);
        sendMessageCallback.execute(botUtilityService.buildSendMessage(BotResponses.triggerReceiveNewsMessage(user), chatId));
    }
}
