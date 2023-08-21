package com.ledikom.service;

import com.ledikom.model.Coupon;
import com.ledikom.model.PollOption;
import com.ledikom.model.User;
import com.ledikom.repository.UserRepository;
import jakarta.persistence.*;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.polls.Poll;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
public class UserService {
    private static final int INIT_REFERRAL_COUNT = 0;
    private static final boolean INIT_RECEIVE_NEWS = true;

    private final UserRepository userRepository;
    private final CouponService couponService;
    private final PollService pollService;

    public UserService(final UserRepository userRepository, @Lazy final CouponService couponService, final PollService pollService) {
        this.userRepository = userRepository;
        this.couponService = couponService;
        this.pollService = pollService;
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public void saveAll(final List<User> users) {
        userRepository.saveAll(users);
    }

    public void addNewUser(final Long chatId) {
        User user = new User(chatId, INIT_REFERRAL_COUNT, INIT_RECEIVE_NEWS);
        userRepository.save(user);
        couponService.addCouponsToUser(user);
    }

    public void saveUser(final User user) {
        userRepository.save(user);
    }

    public User findByChatId(final Long chatId) {
        return userRepository.findByChatId(chatId).orElseThrow(() -> new RuntimeException("User not found"));
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String question;
    @ElementCollection
    @CollectionTable(name = "poll_option", joinColumns = @JoinColumn(name = "poll_id"))
    private List<PollOption> options;
    private Integer totalVoterCount;
    private String type;
    private Boolean allowMultipleAnswers;
    private Integer correctOptionId;
    private String explanation;

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

    public void removeCouponFromUser(final User user, final Coupon coupon) {
        user.getCoupons().remove(coupon);
        userRepository.save(user);
    }

    public void addNewRefUser(final long chatIdFromRefLink, final long chatId) {
        final boolean selfLinkOrUserExists = chatIdFromRefLink == chatId || userExistsByChatId(chatId);
        if (!selfLinkOrUserExists) {
            User user = userRepository.findByChatId(chatIdFromRefLink).orElseThrow(() -> new RuntimeException("User not found"));
            user.setReferralCount(user.getReferralCount() + 1);
            userRepository.save(user);
        }
    }

    public boolean userExistsByChatId(final long chatId) {
        return userRepository.findByChatId(chatId).isPresent();
    }

    public List<User> getAllUsersToReceiveNews() {
        return userRepository.findAll().stream().filter(User::getReceiveNews).collect(Collectors.toList());
    }
}
