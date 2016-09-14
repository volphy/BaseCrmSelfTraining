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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

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
    private String salesRepresentativeEmailPattern;

    private String accountManagerEmailPattern;

    private String favouriteAccountManagerName;

    private static final String FIRST_STAGE_NAME = "incoming";

    private final Client baseClient;

    private final String deviceUuid;

    private final long firstStageId;
    private final long wonStageId;

    private final List<Long> activeStageIds;

    @Autowired
    public WorkflowTask(@Value("${workflow.salesrep.email.pattern}") String salesRepresentativeEmailPattern,
                        @Value("${workflow.accountmanager.email.pattern}") String accountManagerEmailPattern,
                        @Value("${workflow.account.manager.name}") String favouriteAccountManagerName) {

        this.salesRepresentativeEmailPattern = salesRepresentativeEmailPattern;
        this.accountManagerEmailPattern = accountManagerEmailPattern;
        this.favouriteAccountManagerName = favouriteAccountManagerName;

        String accessToken = getAccessToken();

        baseClient = new Client(new Configuration.Builder()
                                        .accessToken(accessToken)
                                        .build());

        firstStageId = getFirstStage().getId();
        wonStageId = getWonStage().getId();

        activeStageIds = baseClient.stages()
                                    .list(new StagesService.SearchCriteria().active(true))
                                    .stream()
                                    .map(Stage::getId)
                                    .collect(toList());

        deviceUuid = getDeviceUuid();
    }

    private Stage getWonStage() {
        return baseClient.stages()
                .list(new StagesService.SearchCriteria().name("won"))
                .stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Won stage of the pipeline not available"));
    }

    private Stage getFirstStage() {
        return baseClient.stages()
                .list(new StagesService.SearchCriteria().name(FIRST_STAGE_NAME))
                .stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("First stage of the pipeline not available"));
    }


    /**
     * Main workflow loop
     */
    @Scheduled(fixedDelay = 5000)
    public void runWorkflow() {
        log.info("Starting workflow run");

        Sync sync = new Sync(baseClient, deviceUuid);

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

    private boolean processContact(final Meta meta, final Contact contact) {
        log.trace("Current contact={}", contact);

        String eventType = meta.getSync().getEventType();
        log.trace("eventType={}", eventType);
        if (eventType.contentEquals("created") || eventType.contentEquals("updated")) {
            log.debug("Contact sync eventType={}", eventType);

            if (shouldNewDealBeCreated(contact)) {
                createNewDeal(contact);
            }
        }
        return true;
    }

    private boolean processDeal(final Meta meta, final Deal deal) {
        log.trace("Current deal={}", deal);

        String eventType = meta.getSync().getEventType();
        log.trace("eventType={}", eventType);
        if (eventType.contentEquals("created") || eventType.contentEquals("updated")) {
            log.debug("Deal sync eventType={}", eventType);

            verifyExistingDeal(deal);
        }
        return true;
    }

    private void verifyExistingDeal(final Deal deal) {
        log.trace("Current deal={}", deal);

        if (isDealStageWon(deal)) {
            log.info("Verifying deal in Won stage");

            Contact dealsContact = fetchExistingContact(deal.getContactId());
            log.trace("Deal's contact={}", dealsContact);

            User contactOwner = fetchOwner(dealsContact.getOwnerId());
            log.trace("Contact's owner={}", contactOwner);

            if (!isContactOwnerAnAccountManager(contactOwner)) {
                updateExistingContact(dealsContact);
            }
        }
    }

    private boolean updateExistingContact(final Contact dealsContact) {
        log.info("Updating contact's owner");

        Long accountManagerId = Optional.ofNullable(getUserByName(favouriteAccountManagerName).getId())
                .orElseThrow(() -> new NoSuchElementException("User " + favouriteAccountManagerName + " not available"));

        log.trace("accountManagerId={}", accountManagerId);

        Map<String, Object> contactAttributes = new HashMap<>();
        contactAttributes.put("owner_id", accountManagerId);
        Contact updatedContact = baseClient.contacts()
                                        .update(dealsContact.getId(), contactAttributes);

        log.debug("Updated contact={}", updatedContact);

        return true;
    }

    private User getUserByName(final String name) {
        List<User> foundUsers = baseClient.users()
                                    .list(new UsersService.SearchCriteria().name(name));

        return foundUsers.isEmpty() ? null : foundUsers.get(0);
    }

    private boolean isContactOwnerAnAccountManager(final User user) {
        return user.getEmail()
                .contains(accountManagerEmailPattern);
    }

    private Contact fetchExistingContact(final Long contactId) {
        return baseClient.contacts()
                .get(contactId);
    }

    private boolean isDealStageWon(final Deal deal) {
        return deal.getStageId()
                .equals(wonStageId);
    }

    private void createNewDeal(final Contact newContact) {
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

    private boolean shouldNewDealBeCreated(final Contact contact) {
        boolean isContactACompany = contact.getIsOrganization();
        log.trace("isContactACompany={}", isContactACompany);

        long ownerId = contact.getOwnerId();
        User owner = fetchOwner(ownerId);
        log.trace("Contact's owner={}", owner);

        long contactId = contact.getId();
        log.trace("Contact's id={}", contactId);

        boolean isUserSalesRepresentative = owner.getEmail()
                                                    .contains(salesRepresentativeEmailPattern);
        log.trace("isUserSalesRepresentative={}", isUserSalesRepresentative);

        log.trace("No deals found={}", areNoActiveDealsFound(contactId));

        boolean result = isContactACompany && isUserSalesRepresentative && areNoActiveDealsFound(contactId);
        log.debug("Should new deal be created={}", result);

        return result;
    }

    private boolean areNoActiveDealsFound(final Long contactId) {
        return baseClient.deals()
                            .list(new DealsService.SearchCriteria().contactId(contactId))
                            .stream()
                            .filter(d -> activeStageIds.contains(d.getStageId()))
                            .collect(toList())
                            .isEmpty();
    }

    private User fetchOwner(final long userId) {
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
