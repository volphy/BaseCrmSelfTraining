package com.solidbrain.services;

import com.codahale.metrics.annotation.Timed;
import com.getbase.Client;
import com.getbase.models.Contact;
import com.getbase.models.Deal;
import com.getbase.models.Stage;
import com.getbase.models.User;
import com.getbase.services.DealsService;
import com.getbase.services.StagesService;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static java.util.stream.Collectors.toList;

/**
 * Created by Krzysztof Wilk on 17/10/2016.
 */

@Slf4j
@Service
public class ContactService {

    private Client baseClient;

    private String dealNameDateFormat;

    private List<String> salesRepresentativesEmails;

    private UserService userService;

    @Autowired
    public ContactService(Client client,
                          @Value("${workflow.deal.name.date.format}") String dealNameFormat,
                          @Qualifier("salesReps") List<String> salesRepresentativesEmails,
                          UserService userService) {

        this.baseClient = client;
        this.dealNameDateFormat = dealNameFormat;
        this.salesRepresentativesEmails = salesRepresentativesEmails;
        this.userService = userService;
    }

    @SuppressWarnings("squid:S1192")
    @Timed(name = "processContact")
    public boolean processContact(final String eventType, final Contact contact) {
        MDC.put("contactId", contact.getId().toString());
        log.debug("Processing current contact");

        boolean processingStatus = true;
        if (eventType.contentEquals("created") || eventType.contentEquals("updated")) {
            log.debug("Contact sync eventType={}", eventType);

            MDC.clear();
            try {
                if (shouldNewDealBeCreated(contact)) {
                    createNewDeal(contact);
                }
            } catch (Exception e) {
                processingStatus = false;
                log.error("Cannot process contact (id={}). Message={})", contact.getId(), e.getMessage(), e);
            }

        }
        return processingStatus;
    }

    private boolean shouldNewDealBeCreated(final Contact contact) {
        boolean isContactACompany = contact.getIsOrganization();
        log.debug("Is current contact a company={}", isContactACompany);

        User owner = userService.getContactOwner(contact);
        log.debug("Contact's owner={}", owner);

        long contactId = contact.getId();
        log.debug("Contact's id={}", contactId);

        boolean isUserSalesRepresentative = salesRepresentativesEmails.contains(owner.getEmail());
        log.debug("Is contact's owner a sales representative={}", isUserSalesRepresentative);

        boolean activeDealsMissing = areNoActiveDealsFound(contactId);
        log.debug("No deals found={}", activeDealsMissing);

        boolean result = isContactACompany && isUserSalesRepresentative && activeDealsMissing;
        log.debug("Should new deal be created={}", result);

        return result;
    }

    private boolean areNoActiveDealsFound(final Long contactId) {
        List<Long> activeStageIds = baseClient.stages()
                .list(new StagesService.SearchCriteria().active(true))
                .stream()
                .map(Stage::getId)
                .collect(toList());

        return baseClient.deals()
                .list(new DealsService.SearchCriteria().contactId(contactId))
                .stream()
                .noneMatch(d -> activeStageIds.contains(d.getStageId()));
    }

    @SuppressWarnings("squid:S1192")
    private void createNewDeal(final Contact newContact) {
        MDC.put("contactId", newContact.getId().toString());
        log.info("Creating new deal");

        DateTimeFormatter selectedFormatter;
        try {
            selectedFormatter = DateTimeFormatter.ofPattern(dealNameDateFormat);
        } catch (IllegalArgumentException e) {
            log.error("Illegal date format. Property workflow.deal.name.date.format={} Message={}",
                    dealNameDateFormat,
                    e.getMessage(),
                    e);
            log.error("Setting default to ISO local date format: yyyy-MM-dd");

            selectedFormatter = DateTimeFormatter.ISO_LOCAL_DATE;
        }

        String dateSuffix = ZonedDateTime.now()
                .toLocalDate()
                .format(selectedFormatter);

        String dealName = newContact.getName() + " " + dateSuffix;

        Deal newDeal = new Deal();
        newDeal.setName(dealName);
        newDeal.setContactId(newContact.getId());
        newDeal.setOwnerId(newContact.getOwnerId());

        log.info("New deal={}", newDeal);
        Deal newlyCreatedDeal = baseClient.deals()
                .create(newDeal);

        MDC.clear();
        log.debug("Created new deal={}", newlyCreatedDeal);
    }
}
