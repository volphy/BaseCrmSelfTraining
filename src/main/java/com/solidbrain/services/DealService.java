package com.solidbrain.services;

import com.codahale.metrics.annotation.Timed;
import com.getbase.Client;
import com.getbase.models.Contact;
import com.getbase.models.Deal;
import com.getbase.models.User;
import com.getbase.services.StagesService;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
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
public class DealService {

    private Client baseClient;

    private List<String> accountManagersEmails;
    private String accountManagerOnDutyEmail;

    private UserService userService;

    @Autowired
    public DealService(Client client,
                       List<String> accountManagersEmails,
                       String accountManagerOnDutyEmail,
                       UserService userService) {
        this.baseClient = client;

        this.accountManagersEmails = accountManagersEmails;
        this.accountManagerOnDutyEmail = accountManagerOnDutyEmail;

        this.userService = userService;
    }

    @Timed(name = "processDeal")
    public boolean processDeal(final String eventType, final Deal deal) {
        MDC.put("dealId", deal.getId().toString());
        log.debug("Processing current deal");

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
        log.debug("Processing recently modified deal={}", deal);

        if (isDealStageWon(deal)) {
            log.info("Verifying deal in Won stage");

            MDC.clear();
            Contact dealsContact = fetchExistingContact(deal.getContactId());
            log.debug("Deal's contact={}", dealsContact);

            User contactOwner = userService.getContactOwner(dealsContact);
            log.debug("Contact's owner={}", contactOwner);

            if (!isContactOwnerAnAccountManager(contactOwner)) {
                updateExistingContact(dealsContact);
            }
        }
    }

    private Contact fetchExistingContact(final Long contactId) {
        return baseClient.contacts()
                .get(contactId);
    }

    private boolean isContactOwnerAnAccountManager(final User user) {
        return accountManagersEmails.contains(user.getEmail());
    }

    private boolean isDealStageWon(final Deal deal) {
        return baseClient.stages()
                .list(new StagesService.SearchCriteria().active(false))
                .stream()
                .anyMatch(s -> s.getCategory().contentEquals("won") && deal.getStageId().equals(s.getId()));
    }

    @SuppressWarnings("squid:S1192")
    private boolean updateExistingContact(final Contact dealsContact) {
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
}
