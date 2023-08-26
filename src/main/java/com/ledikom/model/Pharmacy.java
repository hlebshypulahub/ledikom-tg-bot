package com.ledikom.model;

import com.ledikom.utils.City;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Entity
@AllArgsConstructor
@NoArgsConstructor
public class Pharmacy {

    @Id
    private int id;
    private String name;
    private City city;
    private String openHours;
    private String phoneNumber;
    private String gmapsLink;

}
