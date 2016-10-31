package com.solidbrain.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static java.util.stream.Collectors.toList;

/**
 * Created by Krzysztof Wilk on 27/10/2016.
 */
@Configuration
@Slf4j
public class EmailsConfig {

    @Bean
    @Qualifier("salesReps")
    public List<String> getEmailsOfSalesRepresentatives() {
        String emails = System.getProperty("workflow.sales.representatives.emails",
                System.getenv("workflow_sales_representatives_emails"));
        log.debug("Sales representatives emails (raw)={}", emails);

        return Arrays.stream(Optional.ofNullable(emails)
                .orElseThrow(() -> new IllegalStateException("Empty list of sales representatives' emails"))
                .replaceAll(" ", "")
                .split(","))
                .collect(toList());
    }

    @Bean
    @Qualifier("accountManagers")
    public List<String> getEmailsOfAccountManagers() {
        String emails = System.getProperty("workflow.account.managers.emails",
                System.getenv("workflow_account_managers_emails"));
        log.debug("Account managers emails (raw)={}", emails);

        return Arrays.stream(Optional.ofNullable(emails)
                .orElseThrow(() -> new IllegalStateException("Empty list of account managers emails"))
                .replaceAll(" ", "")
                .split(","))
                .collect(toList());
    }

    @Bean
    @Qualifier("accountManager")
    public String getAccountManagerOnDuty() {
        String email = System.getProperty("workflow.account.manager.on.duty.email",
                System.getenv("workflow_account_manager_on_duty_email"));
        log.debug("Account manager on duty email={}", email);

        return Optional.ofNullable(email)
                .orElseThrow(() -> new IllegalStateException("Empty email of the manager on duty"))
                .trim();
    }
}
