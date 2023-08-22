package com.ledikom.model;

import com.ledikom.utils.UserResponseState;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;
import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "user_ledikom")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long chatId;
    private Integer referralCount;
    private Boolean receiveNews;
    private UserResponseState responseState;
    private String note;

    public User(final Long chatId, final Integer referralCount, final Boolean receiveNews, final UserResponseState responseState) {
        this.chatId = chatId;
        this.referralCount = referralCount;
        this.receiveNews = receiveNews;
        this.responseState = responseState;
    }

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "user_coupon",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "coupon_id")
    )
    private Set<Coupon> coupons = new HashSet<>();
}
