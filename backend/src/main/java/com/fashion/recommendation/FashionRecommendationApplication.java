package com.fashion.recommendation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FashionRecommendationApplication {
    public static void main(String[] args) {
        SpringApplication.run(FashionRecommendationApplication.class, args);
    }
}
