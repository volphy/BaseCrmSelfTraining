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

        this.contactService = new ContactService(baseClient, accountManagerOnDutyEmail);
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

        this.contactService = new ContactService(baseClient, accountManagerOnDutyEmail);
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
                .subscribe(Contact.class, (meta, contact) -> processContact(meta.getSync().getEventType(), contact))
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

    @SuppressWarnings("squid:S1192")
     boolean processContact(final String eventType, final Contact contact) {
        MDC.put("contactId", contact.getId().toString());
        log.trace("Processing current contact");

        boolean processingStatus = true;
        if (eventType.contentEquals("created") || eventType.contentEquals("updated")) {
            log.debug("Contact sync eventType={}", eventType);

            MDC.clear();
            try {
                if (dealService.shouldNewDealBeCreated(contact)) {
                    dealService.createNewDeal(contact);
                }
            } catch (Exception e) {
                processingStatus = false;
                log.error("Cannot process contact (id={}). Message={})", contact.getId(), e.getMessage(), e);
            }

        }
        return processingStatus;
    }

    boolean processDeal(final String eventType, final Deal deal) {
        MDC.put("dealId", deal.getId().toString());
        log.trace("Processing current deal");

        boolean processingStatus = true;
        if (eventType.contentEquals("created") || eventType.contentEquals("updated")) {
            log.debug("Deal sync event type={}", eventType);

            try {
                processRecentlyModifiedDeal(deal);
            } catch (Exception e) {
                processingStatus = false;
                log.error("Cannot process deal (id={}). Message={})", deal.getId(), e.getMessage(), e);
            }
        }

        return processingStatus;
    }

    private void processRecentlyModifiedDeal(final Deal deal) {
        log.trace("Processing recently modified deal={}", deal);

        if (dealService.isDealStageWon(deal)) {
            log.info("Verifying deal in Won stage");

            MDC.clear();
            Contact dealsContact = contactService.fetchExistingContact(deal.getContactId());
            log.trace("Deal's contact={}", dealsContact);

            User contactOwner = contactService.getContactOwner(dealsContact);
            log.trace("Contact's owner={}", contactOwner);

            if (!isContactOwnerAnAccountManager(contactOwner)) {
                contactService.updateExistingContact(dealsContact);
            }
        }
    }

    private boolean isContactOwnerAnAccountManager(final User user) {
        return accountManagersEmails.contains(user.getEmail());
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
        log.trace("Sales representatives emails (raw)={}", emails);

        return Arrays.stream(Optional.ofNullable(emails)
                        .orElseThrow(() -> new IllegalStateException("Empty list of sales representatives' emails"))
                        .replaceAll(" ", "")
                        .split(","))
                        .collect(toList());
    }

    private List<String> getEmailsOfAccountManagers() {
        String emails = System.getProperty("workflow.account.managers.emails",
                                System.getenv("workflow_account_managers_emails"));
        log.trace("Account managers emails (raw)={}", emails);

        return Arrays.stream(Optional.ofNullable(emails)
                        .orElseThrow(() -> new IllegalStateException("Empty list of account managers emails"))
                        .replaceAll(" ", "")
                        .split(","))
                        .collect(toList());
    }

    private String getAccountManagerOnDuty() {
        String email = System.getProperty("workflow.account.manager.on.duty.email",
                                System.getenv("workflow_account_manager_on_duty_email"));
        log.trace("Account manager on duty email={}", email);

        return Optional.ofNullable(email)
                .orElseThrow(() -> new IllegalStateException("Empty email of the manager on duty"))
                .trim();
    }
}
