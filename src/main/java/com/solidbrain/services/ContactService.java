package com.solidbrain.services;

import com.getbase.Client;
import com.getbase.models.Contact;
import com.getbase.models.User;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.util.HashMap;
import java.util.Map;
import java.util.MissingResourceException;

/**
 * Created by Krzysztof Wilk on 17/10/2016.
 */

@Slf4j
public class ContactService {

    private Client baseClient;

    private String accountManagerOnDutyEmail;

    private UserService userService;

    public ContactService(Client client, String accountManagerOnDutyEmail) {
        this.baseClient = client;
        this.accountManagerOnDutyEmail = accountManagerOnDutyEmail;
        this.userService = new UserService(client);
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

        log.trace("Account Manager's Id={}", accountManager.getId());

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
}
