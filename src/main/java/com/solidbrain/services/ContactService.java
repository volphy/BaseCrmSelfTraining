package com.solidbrain.services;

import com.codahale.metrics.annotation.Timed;
import com.getbase.Client;
import com.getbase.models.Contact;
import com.getbase.models.User;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.MissingResourceException;

/**
 * Created by Krzysztof Wilk on 17/10/2016.
 */

@Slf4j
@Service
public class ContactService {

    private Client baseClient;

    private List<String> accountManagersEmails;
    private String accountManagerOnDutyEmail;

    private UserService userService;

    private DealService dealService;

    @Autowired
    public ContactService(Client client,
                          @Qualifier("accountManagers") List<String> accountManagersEmails,
                          @Qualifier("accountManager") String accountManagerOnDutyEmail,
                          @Qualifier("salesReps") List<String> salesRepresentativesEmails,
                          DealService dealService,
                          UserService userService) {

        this.baseClient = client;

        this.accountManagersEmails = accountManagersEmails;
        this.accountManagerOnDutyEmail = accountManagerOnDutyEmail;

        this.userService = userService;
        this.dealService = dealService;
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


    @SuppressWarnings("squid:S1192")
    public boolean updateExistingContact(final Contact dealsContact) {
        MDC.put("contactId", dealsContact.getId().toString());
        log.info("Updating contact's owner");
        MDC.clear();

        User accountManager = userService.getUserByEmail(accountManagerOnDutyEmail)
                .orElseThrow(() -> new MissingResourceException("User not found",
                        "com.getbase.models.User",
                        accountManagerOnDutyEmail));

        log.debug("Account Manager's Id={}", accountManager.getId());

        Map<String, Object> contactAttributes = new HashMap<>();
        contactAttributes.put("owner_id", accountManager.getId());
        Contact updatedContact = baseClient.contacts()
                .update(dealsContact.getId(), contactAttributes);

        log.debug("Updated contact={}", updatedContact);

        return true;
    }

    public Contact fetchExistingContact(final Long contactId) {
        return baseClient.contacts()
                .get(contactId);
    }

    public User getContactOwner(Contact contact) {
        return userService.getUserById(contact.getOwnerId());
    }

    public boolean isContactOwnerAnAccountManager(final User user) {
        return accountManagersEmails.contains(user.getEmail());
    }
}
