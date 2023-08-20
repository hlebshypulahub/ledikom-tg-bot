package com.ledikom.service;

import com.ledikom.model.Poll;
import com.ledikom.model.PollOption;
import com.ledikom.repository.PollRepository;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

@Service
public class PollService {

    private final PollRepository pollRepository;

    public PollService(final PollRepository pollRepository) {
        this.pollRepository = pollRepository;
    }

    public Poll getPoll(final org.telegram.telegrambots.meta.api.objects.polls.Poll telegramPoll) {
        return new Poll(telegramPoll.getQuestion(),
                telegramPoll.getOptions().stream().map(tgPollOption -> new PollOption(tgPollOption.getText(), tgPollOption.getVoterCount())).collect(Collectors.toList()),
                telegramPoll.getTotalVoterCount(), telegramPoll.getIsAnonymous(), telegramPoll.getType(),
                telegramPoll.getAllowMultipleAnswers(), telegramPoll.getCorrectOptionId(), telegramPoll.getExplanation());
    }

    public void savePoll(final Poll poll) {
        pollRepository.save(poll);
    }
}
