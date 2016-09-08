package com.solidbrain;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
class SelfTrainingApplication {

	public static void main(String[] args) {
		SpringApplication.run(SelfTrainingApplication.class, args);
	}
}
