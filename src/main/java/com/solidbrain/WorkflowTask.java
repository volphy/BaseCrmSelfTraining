package com.solidbrain;

import com.getbase.Client;
import com.getbase.Configuration;
import com.getbase.models.Contact;
import com.getbase.services.ContactsService;
import com.getbase.sync.Sync;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Optional;

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

    private Client baseClient;

    private Sync sync;

    public WorkflowTask() {
        accessToken = getAccessToken();
        deviceUuid = getDeviceUuid();

        baseClient = new Client(new Configuration.Builder()
                .accessToken(getAccessToken())
                .build());

        sync = new Sync(baseClient, deviceUuid);
        sync.subscribe(Contact.class, (meta, contact) -> true);
    }


    @Scheduled(fixedDelay = 5000)
    public void reportCurrentTime() {
        LOG.info("The time is now {}", dateFormat.format(new Date()));
        sync.fetch();

        List<Contact> fetchedContacts = baseClient.contacts().list(new ContactsService.SearchCriteria().name(SAMPLE_COMPANY_NAME));

        Contact sampleContact = null;
        if (!fetchedContacts.isEmpty()) {
            sampleContact = fetchedContacts.get(0);
        }

        LOG.info("Found new contact=" + sampleContact);
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
