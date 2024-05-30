package com.example.tuningagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TuningAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(TuningAgentApplication.class, args);
    }

}
