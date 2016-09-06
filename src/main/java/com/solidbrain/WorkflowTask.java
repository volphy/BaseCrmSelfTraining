package com.solidbrain;

import com.getbase.Client;
import com.getbase.Configuration;
import com.getbase.models.Contact;
import com.getbase.sync.Sync;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by Krzysztof Wilk on 06/09/16.
 */
@Component
public class WorkflowTask {
    private static final Logger LOG = LoggerFactory.getLogger(WorkflowTask.class);

    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");

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
    }

    private String getAccessToken() {
        return System.getProperty("BASE_CRM_TOKEN", System.getenv("BASE_CRM_TOKEN"));
    }

    private String getDeviceUuid() {
        return System.getProperty("DEVICE_UUID", System.getenv("DEVICE_UUID"));
    }
}
