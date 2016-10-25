package com.solidbrain.workflow;

import com.getbase.Client;
import com.getbase.Configuration;
import com.getbase.models.*;
import com.getbase.sync.Sync;
import com.solidbrain.services.ContactService;
import com.solidbrain.services.DealService;
import lombok.extern.slf4j.Slf4j;

import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.*;


import static java.util.stream.Collectors.toList;

/**
 * Created by Krzysztof Wilk on 06/09/16.
 */
@Component
@Slf4j
class WorkflowTask {
    private List<String> salesRepresentativesEmails;
    private List<String> accountManagersEmails;

    private String accountManagerOnDutyEmail;

    private String dealNameDateFormat;

    private Client baseClient;

    private final String deviceUuid;

    private Sync sync;

    private DealService dealService;
    private ContactService contactService;

    @Autowired
    public WorkflowTask(@Value("${workflow.deal.name.date.format}") String dealNameDateFormat) {
        this.dealNameDateFormat = dealNameDateFormat;

        deviceUuid = getDeviceUuid();

        accountManagersEmails = getEmailsOfAccountManagers();
        log.debug("accountManagersEmails={}", accountManagersEmails);

        accountManagerOnDutyEmail = getAccountManagerOnDuty();
        log.debug("accountManagerOnDutyEmail={}", accountManagerOnDutyEmail);

        salesRepresentativesEmails = getEmailsOfSalesRepresentatives();
        log.debug("salesRepresentativesEmails={}", salesRepresentativesEmails);
    }

    /**
     * This method is executed only in workflow simulation context.
     */
    @PostConstruct
    public void initialize() {
        log.info("Running initialize");
        String accessToken = getAccessToken();
        this.baseClient = new Client(new Configuration.Builder()
                                        .accessToken(accessToken)
                                        .build());
        this.sync = new Sync(baseClient, deviceUuid);

        this.contactService = new ContactService(baseClient, dealNameDateFormat, accountManagersEmails, accountManagerOnDutyEmail, salesRepresentativesEmails);
        this.dealService = new DealService(baseClient, dealNameDateFormat, salesRepresentativesEmails, contactService);
    }

    /**
     * This method is useful for unit testing because
     *  initialize() is not executed in unit tests context.
     */
    void initialize(Client client,
                    Sync sync,
                    String dealNameDateFormat,
                    List<String> accountManagersEmails,
                    String accountManagerOnDutyEmail,
                    List<String> salesRepresentativesEmails) {
        this.baseClient = client;
        this.sync = sync;

        this.dealNameDateFormat = dealNameDateFormat;

        this.accountManagersEmails = accountManagersEmails;
        this.accountManagerOnDutyEmail = accountManagerOnDutyEmail;
        this.salesRepresentativesEmails = salesRepresentativesEmails;

        this.contactService = new ContactService(baseClient, dealNameDateFormat, accountManagersEmails, accountManagerOnDutyEmail, salesRepresentativesEmails);
        this.dealService = new DealService(client, dealNameDateFormat, salesRepresentativesEmails, contactService);
    }

    /**
     * Main workflow loop
     */
    @Scheduled(fixedDelayString = "${workflow.loop.interval}")
    public void runWorkflow() {
        log.info("Starting workflow run");

        // Workaround: https://gist.github.com/michal-mally/73ea265718a0d29aac350dd81528414f
        sync.subscribe(Account.class, (meta, account) -> true)
                .subscribe(Address.class, (meta, address) -> true)
                .subscribe(AssociatedContact.class, (meta, associatedContact) -> true)
                .subscribe(Contact.class, (meta, contact) ->  processContact(meta.getSync().getEventType(), contact))
                .subscribe(Deal.class, (meta, deal) -> processDeal(meta.getSync().getEventType(), deal))
                .subscribe(LossReason.class, (meta, lossReason) -> true)
                .subscribe(Note.class, (meta, note) -> true)
                .subscribe(Pipeline.class, (meta, pipeline) -> true)
                .subscribe(Source.class, (meta, source) -> true)
                .subscribe(Stage.class, (meta, stage) -> true)
                .subscribe(Tag.class, (meta, tag) -> true)
                .subscribe(Task.class, (meta, task) -> true)
                .subscribe(User.class, (meta, user) -> true)
                .subscribe(Lead.class, (meta, lead) -> true)
                .fetch();
    }

    boolean processContact(String eventType, Contact contact) {
        return contactService.processContact(eventType, contact);
    }

    boolean processDeal(String eventType, Deal deal) {
        return dealService.processDeal(eventType, deal);
    }




    private String getAccessToken() {
        return Optional.ofNullable(System.getProperty("BASE_CRM_TOKEN", System.getenv("BASE_CRM_TOKEN"))).
                orElseThrow(() -> new IllegalStateException("Missing Base CRM OAuth2 token"));
    }

    private String getDeviceUuid() {
        return Optional.ofNullable(System.getProperty("DEVICE_UUID", System.getenv("DEVICE_UUID"))).
                orElseThrow(() -> new IllegalStateException("Missing Base CRM device uuid"));
    }

    private List<String> getEmailsOfSalesRepresentatives() {
        String emails = System.getProperty("workflow.sales.representatives.emails",
                                System.getenv("workflow_sales_representatives_emails"));
        log.debug("Sales representatives emails (raw)={}", emails);

        return Arrays.stream(Optional.ofNullable(emails)
                        .orElseThrow(() -> new IllegalStateException("Empty list of sales representatives' emails"))
                        .replaceAll(" ", "")
                        .split(","))
                        .collect(toList());
    }

    private List<String> getEmailsOfAccountManagers() {
        String emails = System.getProperty("workflow.account.managers.emails",
                                System.getenv("workflow_account_managers_emails"));
        log.debug("Account managers emails (raw)={}", emails);

        return Arrays.stream(Optional.ofNullable(emails)
                        .orElseThrow(() -> new IllegalStateException("Empty list of account managers emails"))
                        .replaceAll(" ", "")
                        .split(","))
                        .collect(toList());
    }

    private String getAccountManagerOnDuty() {
        String email = System.getProperty("workflow.account.manager.on.duty.email",
                                System.getenv("workflow_account_manager_on_duty_email"));
        log.debug("Account manager on duty email={}", email);

        return Optional.ofNullable(email)
                .orElseThrow(() -> new IllegalStateException("Empty email of the manager on duty"))
                .trim();
    }
}
