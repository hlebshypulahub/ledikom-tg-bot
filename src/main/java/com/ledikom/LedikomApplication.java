package com.ledikom;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {
        "com.ledikom.bot",
        "org.telegram.telegrambots"
})
public class LedikomApplication {

    public static void main(String[] args) {
        SpringApplication.run(LedikomApplication.class, args);
    }

}
