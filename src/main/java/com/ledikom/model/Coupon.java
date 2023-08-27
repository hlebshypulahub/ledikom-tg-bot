package com.ledikom.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@Entity
public class Coupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDateTime startDate;
    private LocalDateTime endDate;

    private String name;
    private String text;
    private String news;
    private String type;
    private int dailyQuantity;
    private int quantityUsed;

    @ManyToMany(fetch = FetchType.EAGER)
    private Set<Pharmacy> pharmacies = new HashSet<>();

    @ManyToMany(mappedBy = "coupons", fetch = FetchType.EAGER)
    private Set<User> users = new HashSet<>();

    public Coupon(final String type, final LocalDateTime startDate, final LocalDateTime endDate, final int dailyQuantity, final int quantityUsed, final List<Pharmacy> pharmacies, final String name, final String text, final String news) {
        this.type = type;
        this.startDate = startDate;
        this.endDate = endDate;
        this.dailyQuantity = dailyQuantity;
        this.quantityUsed = quantityUsed;
        this.pharmacies = new HashSet<>(pharmacies);
        this.name = name;
        this.text = text;
        this.news = news;
    }

    public Coupon(final String name, final String text, final String type, final int dailyQuantity) {
        this.name = name;
        this.text = text;
        this.type = type;
        this.dailyQuantity = dailyQuantity;
    }

//    coupon&
//            03&
//            260823-270823&
//            2000&
//            1,4,5&
//            5% на зубную пасту&
//    В честь дня стоматолога получите купон 5% на зубную пасту марки Colgate&
//    Здесь какой-то расширенный текст от сммщика (типа новость к которой будет прикреплен купон)&

}
