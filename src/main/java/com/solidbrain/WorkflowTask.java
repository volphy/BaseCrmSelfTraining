package com.solidbrain;

import com.getbase.Client;
import com.getbase.Configuration;
import com.getbase.models.*;
import com.getbase.services.DealsService;
import com.getbase.services.StagesService;
import com.getbase.services.UsersService;
import com.getbase.sync.Meta;
import com.getbase.sync.Sync;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static java.util.stream.Collectors.toList;

/**
 * Created by Krzysztof Wilk on 06/09/16.
 */
@Component
@Slf4j
class WorkflowTask {
    @Value("${workflow.salesrep.email.pattern}")
    private String salesRepresentativeEmailPattern;

    @Value("${workflow.accountmanager.email.pattern}")
    private String accountManagerEmailPattern;

    @Value("${workflow.account.manager.name}")
    private String favouriteAccountManagerName;

    @Value("${workflow.stage.first.name}")
    private String firstStageName;

    @Value("${workflow.stage.won.name}")
    private String wonStageName;

    @Value("${workflow.event.type.created.name}")
    private String createdEventName;

    @Value("${workflow.event.type.updated.name}")
    private String updatedEventName;

    @Value("${workflow.log.time.format}")
    private String dateFormat;
    private DateTimeFormatter logDateFormat;

    private final Client baseClient;

    private Sync sync;

    private final String deviceUuid;

    private static Long firstStageId;
    private static Long wonStageId;

    private static List<Long> activeStageIds;

    private WorkflowTask() {
        String accessToken = getAccessToken();

        baseClient = new Client(new Configuration.Builder()
                                        .accessToken(accessToken)
                                        .build());

        firstStageId = Optional.ofNullable(getFirstStageId()).
                orElseThrow(() -> new NoSuchElementException("First (incoming) stage of the pipeline not available"));

        wonStageId = Optional.ofNullable(getWonStageId()).
                orElseThrow(() -> new NoSuchElementException("Won stage of the pipeline not available"));

        activeStageIds = baseClient.stages()
                                    .list(new StagesService.SearchCriteria().active(true))
                                    .stream()
                                    .map(Stage::getId)
                                    .collect(toList());

        deviceUuid = getDeviceUuid();
    }

    private Long getWonStageId() {
        List<Stage> stages = baseClient.stages()
                                        .list(new StagesService.SearchCriteria().name(wonStageName));
        return stages.isEmpty() ? null : stages.get(0).getId();
    }

    private Long getFirstStageId() {
        List<Stage> stages = baseClient.stages()
                                        .list(new StagesService.SearchCriteria().name(firstStageName));
        return stages.isEmpty() ? null : stages.get(0).getId();
    }

    @PostConstruct
    private void initializeLogDateFormat() {
        this.logDateFormat = DateTimeFormatter.ofPattern(dateFormat);
    }


    /**
     * Main workflow loop
     */
    @Scheduled(fixedDelay = 5000)
    public void runWorkflow() {
        if (log.isInfoEnabled()) {
            log.info("Time {}", logDateFormat.format(ZonedDateTime.now()));
        }

        sync = new Sync(baseClient, deviceUuid);

        // Workaround: https://gist.github.com/michal-mally/73ea265718a0d29aac350dd81528414f
        sync.subscribe(Account.class, (meta, account) -> true)
                .subscribe(Address.class, (meta, address) -> true)
                .subscribe(AssociatedContact.class, (meta, associatedContact) -> true)
                .subscribe(Contact.class, this::processContact)
                .subscribe(Deal.class, this::processDeal)
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

    private boolean processContact(Meta meta, Contact contact) {
        log.trace("Current contact={}", contact);

        String eventType = meta.getSync().getEventType();
        log.trace("eventType={}", eventType);
        if (eventType.contentEquals(createdEventName) || eventType.contentEquals(updatedEventName)) {
            log.debug("Contact sync eventType={}", eventType);

            if (shouldNewDealBeCreated(contact)) {
                createNewDeal(contact);
            }
        }
        return true;
    }

    private boolean processDeal(Meta meta, Deal deal) {
        log.trace("Current deal={}", deal);

        String eventType = meta.getSync().getEventType();
        log.trace("eventType={}", eventType);
        if (eventType.contentEquals(createdEventName) || eventType.contentEquals(updatedEventName)) {
            log.debug("Deal sync eventType={}", eventType);

            verifyExistingDeal(deal);
        }
        return true;
    }

    private void verifyExistingDeal(Deal deal) {
        if (isDealStageWon(deal)) {
            log.info("Verifying deal in Won stage");
            log.debug("Deal={}", deal);

            Contact dealsContact = fetchExistingContact(deal.getContactId());
            log.trace("Deal's contact={}", dealsContact);

            User contactOwner = fetchOwner(dealsContact.getOwnerId());
            log.trace("Contact's owner={}", contactOwner);

            if (!isContactOwnerAnAccountManager(contactOwner)) {
                updateExistingContact(dealsContact);
            }
        }
    }

    private boolean updateExistingContact(Contact dealsContact) {
        log.info("Updating contact's owner");

        Long accountManagerId = Optional.ofNullable(getUserIdByName(favouriteAccountManagerName))
                .orElseThrow(() -> new NoSuchElementException("User " + favouriteAccountManagerName + " not available"));

        if (accountManagerId != null) {
            log.trace("accountManagerId={}", accountManagerId);

            Map<String, Object> contactAttributes = new HashMap<>();
            contactAttributes.put("owner_id", accountManagerId);
            Contact updatedContact = baseClient.contacts()
                                        .update(dealsContact.getId(), contactAttributes);

            log.debug("Updated contact={}", updatedContact);
        }

        return true;
    }

    private Long getUserIdByName(String name) {
        List<User> foundUsers = baseClient.users()
                                    .list(new UsersService.SearchCriteria().name(name));

        return foundUsers.isEmpty() ? null : foundUsers.get(0).getId();
    }

    private boolean isContactOwnerAnAccountManager(User user) {
        return user.getEmail()
                .contains(accountManagerEmailPattern);
    }

    private Contact fetchExistingContact(Long contactId) {
        return baseClient.contacts()
                .get(contactId);
    }

    private boolean isDealStageWon(Deal deal) {
        return deal.getStageId()
                .equals(wonStageId);
    }

    private void createNewDeal(Contact newContact) {
        log.info("Creating new deal");

        Map<String, Object> newDealAttributes = new HashMap<>();
        newDealAttributes.put("contact_id", newContact.getId());
        String dealName = newContact.getName() + " " + ZonedDateTime.now()
                                                            .toLocalDate()
                                                            .format(DateTimeFormatter.ISO_LOCAL_DATE);
        newDealAttributes.put("name", dealName);
        newDealAttributes.put("owner_id", newContact.getOwnerId());
        newDealAttributes.put("stage_id", firstStageId);

        Deal newDeal = baseClient.deals()
                            .create(newDealAttributes);

        log.debug("Created new deal={}", newDeal);
    }

    private boolean shouldNewDealBeCreated(Contact contact) {
        Boolean isContactACompany = contact.getIsOrganization();
        log.trace("isContactACompany={}", isContactACompany);

        Long ownerId = contact.getOwnerId();
        User owner = fetchOwner(ownerId);
        log.trace("Contact's owner={}", owner);

        Long contactId = contact.getId();
        log.trace("Contact's id={}", contactId);

        Boolean isUserSalesRepresentative = owner.getEmail()
                                                    .contains(salesRepresentativeEmailPattern);
        log.trace("isUserSalesRepresentative={}", isUserSalesRepresentative);

        log.trace("No deals found={}", areNoActiveDealsFound(contactId));

        boolean result = isContactACompany && isUserSalesRepresentative && areNoActiveDealsFound(contactId);
        log.debug("Should new deal be created={}", result);

        return result;
    }

    private boolean areNoActiveDealsFound(Long contactId) {
        return baseClient.deals()
                            .list(new DealsService.SearchCriteria().contactId(contactId))
                            .stream()
                            .filter(d -> activeStageIds.contains(d.getStageId()))
                            .collect(toList())
                            .isEmpty();
    }

    private User fetchOwner(long userId) {
        return baseClient.users()
                    .get(userId);
    }

    private String getAccessToken() {
        return Optional.ofNullable(System.getProperty("BASE_CRM_TOKEN", System.getenv("BASE_CRM_TOKEN"))).
                orElseThrow(() -> new IllegalStateException("Missing Base CRM OAuth2 token"));
    }

    private String getDeviceUuid() {
        return Optional.ofNullable(System.getProperty("DEVICE_UUID", System.getenv("DEVICE_UUID"))).
                orElseThrow(() -> new IllegalStateException("Missing Base CRM device uuid"));
    }
}
