package com.solidbrain;

import com.getbase.Client;
import com.getbase.Configuration;
import com.getbase.models.Contact;
import com.getbase.models.Deal;
import com.getbase.models.User;
import com.getbase.services.ContactsService;
import com.getbase.services.DealsService;
import com.getbase.services.StagesService;
import com.getbase.sync.Sync;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;


/**
 * Created by Krzysztof Wilk on 06/09/16.
 */
@Component
class WorkflowTask {
    private static final Logger LOG = LoggerFactory.getLogger(WorkflowTask.class);

    private static final String SAMPLE_COMPANY_NAME = "Some Company Of Mine";

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");

    private static String accessToken;

    private static String deviceUuid;

    private final Client baseClient;

    private final Sync sync;

    private static Long firstStageId;
    private static Long wonStageId;

    private static Long accountManagerId;

    private WorkflowTask() {
        accessToken = getAccessToken();
        deviceUuid = getDeviceUuid();

        baseClient = new Client(new Configuration.Builder()
                .accessToken(accessToken)
                .build());

        firstStageId = baseClient.stages().list(new StagesService.SearchCriteria().name("Incoming")).get(0).getId();
        wonStageId = baseClient.stages().list(new StagesService.SearchCriteria().name("Won")).get(0).getId();

        // FIXME - it should not be hard-coded
        accountManagerId = baseClient.users().get(987937L).getId();

        sync = new Sync(baseClient, deviceUuid);
        sync.subscribe(Contact.class, (meta, contact) -> true).
                subscribe(Deal.class, (meta, deal) -> true);
    }


    @Scheduled(fixedDelay = 5000)
    public void runWorkflow() {
        LOG.info("Time {}", dateFormat.format(new Date()));

        Contact newContact = fetchNewContact();

        if (newContact != null) {
            if (shouldNewDealBeCreated(newContact)) {
                createNewDeal(newContact);
            }

            List<Deal> fetchedDeals = fetchAttachedDeals(newContact.getId());
            fetchedDeals.forEach(d -> updateExistingDeal(d));
        }
    }

    private List<Deal> fetchAttachedDeals(Long contactId) {
        sync.fetch();
        return baseClient.deals().list(new DealsService.SearchCriteria().contactId(contactId));
    }

    private void updateExistingDeal(Deal deal) {
        if (isDealStageWon(deal)) {
            LOG.info("Updating deal in Won stage");

            LOG.debug("Deal=" + deal);

            Contact dealsContact = fetchExistingContact(deal.getContactId());
            LOG.debug("Deals contact=" + dealsContact);

            User contactOwner = fetchOwner(dealsContact.getOwnerId());
            LOG.debug("Contact owner=" + contactOwner);

            if (!deal.getOwnerId().equals(contactOwner.getId())) {
                Map<String, Object> contactAttributes = new HashMap<>();
                contactAttributes.put("ownerId", accountManagerId);

                Contact updatedContact = baseClient.contacts().update(dealsContact.getContactId(), contactAttributes);

                LOG.info("Updated company=" + updatedContact);
            }
        }
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
        newDealAttributes.put("contactId", newContact.getId());

        String dealName = newContact.getName() + " " + ZonedDateTime.now().toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE);
        newDealAttributes.put("name", dealName);

        newDealAttributes.put("ownerId", newContact.getOwnerId());
        newDealAttributes.put("stageId", firstStageId);

        Deal newDeal = baseClient.deals().create(newDealAttributes);
        LOG.info("Created new deal=" + newDeal);
    }


    private Contact fetchNewContact() {
        sync.fetch();
        List<Contact> fetchedContacts = baseClient.contacts().list(new ContactsService.SearchCriteria().name(SAMPLE_COMPANY_NAME));

        LOG.debug("fetched contacts=" + fetchedContacts);
        return fetchedContacts.isEmpty() ? null : fetchedContacts.get(0);
    }

    private boolean shouldNewDealBeCreated(Contact contact) {
        Boolean isCompany = contact.getIsOrganization();
        LOG.debug("isCompany=" + isCompany);

        Long ownerId = contact.getOwnerId();
        User owner = fetchOwner(ownerId);
        LOG.debug("Contact owner=" + owner);

        Long contactId = contact.getId();
        LOG.debug("contactId=" + contactId);

        Boolean isUserSalesRepresentative = owner.getEmail().contains("+salesrep+");
        LOG.debug("isUserSalesRepresentative=" + isUserSalesRepresentative);

        LOG.debug("existingDealsFound=" + areExistingDealsFound(contactId));
        return isCompany && isUserSalesRepresentative && !areExistingDealsFound(contactId);
    }

    private boolean areExistingDealsFound(Long contactId) {
        sync.fetch();
        return !baseClient.deals().list(new DealsService.SearchCriteria().contactId(contactId)).isEmpty();
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
