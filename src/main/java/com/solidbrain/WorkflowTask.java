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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.text.SimpleDateFormat;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;


/**
 * Created by Krzysztof Wilk on 06/09/16.
 */
@Component
class WorkflowTask {
    private static final Logger LOG = LoggerFactory.getLogger(WorkflowTask.class);

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
                                    collect(Collectors.toList());

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
    @SuppressWarnings("squid:S1612")
    public void runWorkflow() {
        LOG.info("Time {}", logDateFormat.format(new Date()));

        List<Contact> companies = fetchCompanies();

        companies.forEach(c -> processDeals(c));
    }

    @SuppressWarnings("squid:S1612")
    private void processDeals(Contact company) {
        LOG.debug("Fetched contact=" + company);

        if (shouldNewDealBeCreated(company)) {
            createNewDeal(company);
        }

        List<Deal> fetchedDeals = fetchAttachedDeals(company.getId());
        fetchedDeals.forEach(d -> verifyExistingDeal(d));
    }

    private List<Deal> fetchAttachedDeals(Long contactId) {
        sync.fetch();
        return baseClient.deals().list(new DealsService.SearchCriteria().contactId(contactId));
    }

    private void verifyExistingDeal(Deal deal) {
        if (isDealStageWon(deal)) {
            LOG.info("Verifying deal in Won stage");
            LOG.debug("Deal=" + deal);

            Contact dealsContact = fetchExistingContact(deal.getContactId());
            LOG.trace("Deal's contact=" + dealsContact);

            User contactOwner = fetchOwner(dealsContact.getOwnerId());
            LOG.trace("Contact's owner=" + contactOwner);

            if (!isContactOwnerAnAccountManager(contactOwner)) {
                updateExistingContact(dealsContact);
            }
        }
    }

    private void updateExistingContact(Contact dealsContact) {
        LOG.info("Updating contact's owner");

        Long accountManagerId = getUserIdByName(favouriteAccountManagerName);

        if (accountManagerId != null) {
            LOG.trace("accountManagerId=" + accountManagerId);

            Map<String, Object> contactAttributes = new HashMap<>();
            contactAttributes.put("owner_id", accountManagerId);
            Contact updatedContact = baseClient.contacts().update(dealsContact.getId(), contactAttributes);

            LOG.debug("Updated contact=" + updatedContact);
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
        LOG.info("Creating new deal");

        Map<String, Object> newDealAttributes = new HashMap<>();
        newDealAttributes.put("contact_id", newContact.getId());
        String dealName = newContact.getName() + " " + ZonedDateTime.now().toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE);
        newDealAttributes.put("name", dealName);
        newDealAttributes.put("owner_id", newContact.getOwnerId());
        newDealAttributes.put("stage_id", firstStageId);
        Deal newDeal = baseClient.deals().create(newDealAttributes);

        LOG.debug("Created new deal=" + newDeal);
    }


    private List<Contact> fetchCompanies() {
        sync.fetch();
        List<Contact> fetchedCompanies = baseClient.contacts().
                                                    list(new ContactsService.SearchCriteria().isOrganization(true));

        LOG.debug("Fetched companies=" + fetchedCompanies);
        return fetchedCompanies;
    }

    private boolean shouldNewDealBeCreated(Contact contact) {
        Boolean isContactACompany = contact.getIsOrganization();
        LOG.trace("isContactACompany=" + isContactACompany);

        Long ownerId = contact.getOwnerId();
        User owner = fetchOwner(ownerId);
        LOG.trace("Contact's owner=" + owner);

        Long contactId = contact.getId();
        LOG.trace("Contact's id=" + contactId);

        Boolean isUserSalesRepresentative = owner.getEmail().contains(salesRepresentativeEmailPattern);
        LOG.trace("isUserSalesRepresentative=" + isUserSalesRepresentative);

        LOG.trace("No deals found=" + areNoActiveDealsFound(contactId));

        boolean result = isContactACompany && isUserSalesRepresentative && areNoActiveDealsFound(contactId);
        LOG.debug("Should new deal be created=" + result);

        return result;
    }

    private boolean areNoActiveDealsFound(Long contactId) {
        sync.fetch();

        return baseClient.deals().
                            list(new DealsService.SearchCriteria().contactId(contactId)).
                            stream().
                            filter(d -> activeStageIds.contains(d.getStageId())).
                            collect(Collectors.toList()).
                            isEmpty();
    }

    private User fetchOwner(long userId) {
        return baseClient.users().get(userId);
    }

    private String getAccessToken() {
        return Optional.ofNullable(System.getProperty("BASE_CRM_TOKEN", System.getenv("BASE_CRM_TOKEN"))).
                orElse("");
    }

    private String getDeviceUuid() {
        return Optional.ofNullable(System.getProperty("DEVICE_UUID", System.getenv("DEVICE_UUID"))).
                orElse("");
    }
}
