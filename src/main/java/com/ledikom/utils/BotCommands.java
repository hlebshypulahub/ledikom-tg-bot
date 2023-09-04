package com.ledikom.utils;

public enum BotCommands {
    START("/start"),
    COUPONS("/kupony"),
    NOTES("/zametki"),
    MUSIC("/muzyka_dla_sna"),
    REF_LINK("/moya_ssylka"),
    CITY("/moy_gorod"),
    PROMOTION_ACCEPT("promotionAccept"),
    DATE("/osobennaya_data"),
    TRIGGER_NEWS("/vkl_otkl_rassylku");

    public final String label;

    BotCommands(String label) {
        this.label = label;
    }
}
