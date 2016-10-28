package com.solidbrain.services;

import com.getbase.Client;
import com.getbase.models.Contact;
import com.getbase.models.Deal;
import com.getbase.models.Stage;
import com.getbase.models.User;
import com.getbase.services.DealsService;
import com.getbase.services.StagesService;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static java.util.stream.Collectors.toList;

/**
 * Created by Krzysztof Wilk on 17/10/2016.
 */
@Slf4j
public class DealService {

    private Client baseClient;

    private String dealNameDateFormat;
    private List<String> salesRepresentativesEmails;

    private ContactService contactService;

    public DealService(Client client, String dealNameFormat, List<String> salesRepresentativesEmails, ContactService contactService) {
        this.baseClient = client;
        this.dealNameDateFormat = dealNameFormat;
        this.salesRepresentativesEmails = salesRepresentativesEmails;

        this.contactService = contactService;
    }

    public boolean isDealStageWon(final Deal deal) {
        return baseClient.stages()
                .list(new StagesService.SearchCriteria().active(false))
                .stream()
                .anyMatch(s -> s.getCategory().contentEquals("won") && deal.getStageId().equals(s.getId()));
    }

    @SuppressWarnings("squid:S1192")
    public void createNewDeal(final Contact newContact) {
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

        Deal newlyCreatedDeal = baseClient.deals()
                .create(newDeal);

        MDC.clear();
        log.debug("Created new deal={}", newlyCreatedDeal);
    }

    public boolean shouldNewDealBeCreated(final Contact contact) {
        boolean isContactACompany = contact.getIsOrganization();
        log.trace("Is current contact a company={}", isContactACompany);

        User owner = contactService.getContactOwner(contact);
        log.trace("Contact's owner={}", owner);

        long contactId = contact.getId();
        log.trace("Contact's id={}", contactId);

        boolean isUserSalesRepresentative = salesRepresentativesEmails.contains(owner.getEmail());
        log.trace("Is contact's owner a sales representative={}", isUserSalesRepresentative);

        boolean activeDealsMissing = areNoActiveDealsFound(contactId);
        log.trace("No deals found={}", activeDealsMissing);

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
}
