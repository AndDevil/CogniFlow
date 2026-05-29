package com.shr.cogniflow;

//import org.springframework.boot.ApplicationRunner;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
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