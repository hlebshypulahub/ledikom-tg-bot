package com.ledikom.model;

import com.ledikom.utils.City;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@Entity
@AllArgsConstructor
@NoArgsConstructor
public class Pharmacy {

    @Id
    private long id;
    private String name;
    private City city;
    private String openHours;
    private String phoneNumber;
    private String gmapsLink;

    @ManyToMany(mappedBy = "pharmacies", fetch = FetchType.EAGER)
    private Set<Coupon> coupons = new HashSet<>();

    public Pharmacy(final long id, final String name, final City city, final String openHours, final String phoneNumber, final String gmapsLink) {
        this.id = id;
        this.name = name;
        this.city = city;
        this.openHours = openHours;
        this.phoneNumber = phoneNumber;
        this.gmapsLink = gmapsLink;
    }
}
