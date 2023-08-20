package com.ledikom.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@Entity
public class Poll {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String question;
    @ElementCollection
    @CollectionTable(name = "poll_option", joinColumns = @JoinColumn(name = "poll_id"))
    private List<PollOption> options;
    private Integer totalVoterCount;
    private Boolean isAnonymous;
    private String type;
    private Boolean allowMultipleAnswers;
    private Integer correctOptionId;
    private String explanation;

    public Poll(final String question, final List<PollOption> options, final Integer totalVoterCount, final Boolean isAnonymous, final String type, final Boolean allowMultipleAnswers, final Integer correctOptionId, final String explanation) {
        this.question = question;
        this.options = options;
        this.totalVoterCount = totalVoterCount;
        this.isAnonymous = isAnonymous;
        this.type = type;
        this.allowMultipleAnswers = allowMultipleAnswers;
        this.correctOptionId = correctOptionId;
        this.explanation = explanation;
    }
}
