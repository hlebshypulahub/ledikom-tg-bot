package com.ledikom.service;

import com.ledikom.model.Pharmacy;
import com.ledikom.repository.PharmacyRepository;
import com.ledikom.utils.City;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PharmacyService {

    private final PharmacyRepository pharmacyRepository;
    private final BotUtilityService botUtilityService;

    public PharmacyService(final PharmacyRepository pharmacyRepository, final BotUtilityService botUtilityService) {
        this.pharmacyRepository = pharmacyRepository;
        this.botUtilityService = botUtilityService;
    }

    @PostConstruct
    public void initAndPersistPharmacies() {
        Pharmacy pharmacy = new Pharmacy(1, "Аптека №1", City.BORISOV, "опен хаврс", "+11111111", "https://gmaps/1");
        Pharmacy pharmacy2 = new Pharmacy(2, "Аптека №2", City.MINSK, "опен хаврс", "+11111111", "https://gmaps/2");
        Pharmacy pharmacy3 = new Pharmacy(3, "Аптека №3", City.M_GORKA, "опен хаврс", "+11111111", "https://gmaps/3");
        pharmacyRepository.saveAll(List.of(pharmacy, pharmacy2, pharmacy3));
    }

    public void addCitiesButtons(final SendMessage sm) {
        botUtilityService.addCitiesButtons(sm, pharmacyRepository.findAll().stream().map(Pharmacy::getCity).collect(Collectors.toSet()));
    }
}
