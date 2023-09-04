package com.ledikom.model;

import com.ledikom.utils.City;
import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@Entity
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class Pharmacy {

    @Id
    private long id;
    private String name;
    private City city;
    private String address;
    private String openHours;
    private String phoneNumber;
    private String coordinates;

    @ManyToMany(mappedBy = "pharmacies", fetch = FetchType.EAGER)
    private Set<Coupon> coupons = new HashSet<>();

    public Pharmacy(final long id, final String name, final City city, final String address, final String openHours, final String phoneNumber, final String coordinates) {
        this.id = id;
        this.name = name;
        this.city = city;
        this.address = address;
        this.openHours = openHours;
        this.phoneNumber = phoneNumber;
        this.coordinates = coordinates;
    }
}
