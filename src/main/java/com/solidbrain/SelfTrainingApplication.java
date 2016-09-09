package com.solidbrain;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@SuppressWarnings("squid:S1118")
class SelfTrainingApplication {

    /**
     * Spring Boot application entry point
     * @param args
     */
    // https://jira.sonarsource.com/browse/SONARJAVA-1687
	@SuppressWarnings("squid:S2095")
	public static void main(String[] args) {
		SpringApplication.run(SelfTrainingApplication.class, args);
	}
}
