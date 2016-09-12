package com.solidbrain;

import com.getbase.Client;
import com.getbase.Configuration;
import com.getbase.models.Contact;
import com.getbase.models.Deal;
import com.getbase.models.Stage;
import com.getbase.models.User;
import com.getbase.services.ContactsService;
import com.getbase.services.DealsService;
import com.getbase.services.StagesService;
import com.getbase.services.UsersService;
import com.getbase.sync.Sync;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.text.SimpleDateFormat;
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

    @Value("${workflow.log.time.format}")
    private String dateFormat;
    private SimpleDateFormat logDateFormat;

    private final Client baseClient;

    private final Sync sync;

    private static Long firstStageId;
    private static Long wonStageId;

    private static List<Long> activeStageIds;

    private WorkflowTask() {
        String accessToken = getAccessToken();
        String deviceUuid = getDeviceUuid();

        baseClient = new Client(new Configuration.Builder()
                .accessToken(accessToken)
                .build());

        firstStageId = baseClient.stages().list(new StagesService.SearchCriteria().name(firstStageName)).get(0).getId();
        wonStageId = baseClient.stages().list(new StagesService.SearchCriteria().name(wonStageName)).get(0).getId();

        activeStageIds = baseClient.stages().
                                    list(new StagesService.SearchCriteria().active(true)).
                                    stream().
                                    map(Stage::getId).
                                    collect(toList());

        sync = new Sync(baseClient, deviceUuid);
        sync.subscribe(Contact.class, (meta, contact) -> true).
                subscribe(Deal.class, (meta, deal) -> true).
                fetch();
    }

    @PostConstruct
    private void initializeLogDateFormat() {
        this.logDateFormat = new SimpleDateFormat(dateFormat);
    }


    /**
     * Main workflow loop
     */
    @Scheduled(fixedDelay = 5000)
    public void runWorkflow() {
        log.info("Time {}", logDateFormat.format(new Date()));

        List<Contact> companies = fetchCompanies();

        companies.forEach(this::processDeals);
    }

    private void processDeals(Contact company) {
        log.debug("Fetched contact=" + company);

        if (shouldNewDealBeCreated(company)) {
            createNewDeal(company);
        }

        List<Deal> fetchedDeals = fetchAttachedDeals(company.getId());
        fetchedDeals.forEach(this::verifyExistingDeal);
    }

    private List<Deal> fetchAttachedDeals(Long contactId) {
        sync.fetch();
        return baseClient.deals().list(new DealsService.SearchCriteria().contactId(contactId));
    }

    private void verifyExistingDeal(Deal deal) {
        if (isDealStageWon(deal)) {
            log.info("Verifying deal in Won stage");
            log.debug("Deal=" + deal);

            Contact dealsContact = fetchExistingContact(deal.getContactId());
            log.trace("Deal's contact=" + dealsContact);

            User contactOwner = fetchOwner(dealsContact.getOwnerId());
            log.trace("Contact's owner=" + contactOwner);

            if (!isContactOwnerAnAccountManager(contactOwner)) {
                updateExistingContact(dealsContact);
            }
        }
    }

    private void updateExistingContact(Contact dealsContact) {
        log.info("Updating contact's owner");

        Long accountManagerId = getUserIdByName(favouriteAccountManagerName);

        if (accountManagerId != null) {
            log.trace("accountManagerId=" + accountManagerId);

            Map<String, Object> contactAttributes = new HashMap<>();
            contactAttributes.put("owner_id", accountManagerId);
            Contact updatedContact = baseClient.contacts().update(dealsContact.getId(), contactAttributes);

            log.debug("Updated contact=" + updatedContact);
        }
    }

    private Long getUserIdByName(String name) {
        List<User> foundUsers = baseClient.users().
                                            list(new UsersService.SearchCriteria().name(name));

        return foundUsers.isEmpty() ? null : foundUsers.get(0).getId();
    }

    private boolean isContactOwnerAnAccountManager(User user) {
        return user.getEmail().contains(accountManagerEmailPattern);
    }

    private Contact fetchExistingContact(Long contactId) {
        sync.fetch();
        return baseClient.contacts().get(contactId);
    }

    private boolean isDealStageWon(Deal deal) {
        return deal.getStageId().equals(wonStageId);
    }

    private void createNewDeal(Contact newContact) {
        log.info("Creating new deal");

        Map<String, Object> newDealAttributes = new HashMap<>();
        newDealAttributes.put("contact_id", newContact.getId());
        String dealName = newContact.getName() + " " + ZonedDateTime.now().toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE);
        newDealAttributes.put("name", dealName);
        newDealAttributes.put("owner_id", newContact.getOwnerId());
        newDealAttributes.put("stage_id", firstStageId);
        Deal newDeal = baseClient.deals().create(newDealAttributes);

        log.debug("Created new deal=" + newDeal);
    }


    private List<Contact> fetchCompanies() {
        sync.fetch();
        List<Contact> fetchedCompanies = baseClient.contacts().
                                                    list(new ContactsService.SearchCriteria().isOrganization(true));

        log.debug("Fetched companies=" + fetchedCompanies);
        return fetchedCompanies;
    }

    private boolean shouldNewDealBeCreated(Contact contact) {
        Boolean isContactACompany = contact.getIsOrganization();
        log.trace("isContactACompany=" + isContactACompany);

        Long ownerId = contact.getOwnerId();
        User owner = fetchOwner(ownerId);
        log.trace("Contact's owner=" + owner);

        Long contactId = contact.getId();
        log.trace("Contact's id=" + contactId);

        Boolean isUserSalesRepresentative = owner.getEmail().contains(salesRepresentativeEmailPattern);
        log.trace("isUserSalesRepresentative=" + isUserSalesRepresentative);

        log.trace("No deals found=" + areNoActiveDealsFound(contactId));

        boolean result = isContactACompany && isUserSalesRepresentative && areNoActiveDealsFound(contactId);
        log.debug("Should new deal be created=" + result);

        return result;
    }

    private boolean areNoActiveDealsFound(Long contactId) {
        sync.fetch();

        return baseClient.deals().
                            list(new DealsService.SearchCriteria().contactId(contactId)).
                            stream().
                            filter(d -> activeStageIds.contains(d.getStageId())).
                            collect(toList()).
                            isEmpty();
    }

    private User fetchOwner(long userId) {
        return baseClient.users().get(userId);
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
