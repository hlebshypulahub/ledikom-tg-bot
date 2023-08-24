package com.ledikom.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MusicCallbackRequest {
    private String command;
    private int duration;

    public MusicCallbackRequest(final String command) {
        this.command = command;
    }

    public boolean readyToPlay() {
        return command != null && duration != 0;
    }
}
