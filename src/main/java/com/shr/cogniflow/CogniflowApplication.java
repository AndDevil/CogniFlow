package com.shr.cogniflow;

//import org.springframework.boot.ApplicationRunner;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CogniflowApplication {

    public static void main(String[] args) {
        SpringApplication.run(CogniflowApplication.class, args);
    }

//    @Bean
//    ApplicationRunner init(MarketDataService service) {
//        return args -> {
//            service.fetchAndAnalyze("IBM");
//        };
//    }
}