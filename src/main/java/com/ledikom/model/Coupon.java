package com.ledikom.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@Entity
public class Coupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String text;
    private String name;
    private int discount;

    public Coupon(final String text, final String name, final int discount) {
        this.text = text;
        this.name = name;
        this.discount = discount;
    }

    @ManyToMany(mappedBy = "coupons")
    private Set<User> users = new HashSet<>();
}
