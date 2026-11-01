package org.goodroots.foodrescue;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FoodRescueApplication {
    public static void main(String[] args) {
        SpringApplication.run(FoodRescueApplication.class, args);
    }
}
