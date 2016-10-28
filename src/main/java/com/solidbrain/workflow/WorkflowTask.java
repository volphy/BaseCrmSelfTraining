package com.solidbrain.workflow;

import com.getbase.models.*;
import com.getbase.sync.Sync;
import com.solidbrain.services.ContactService;
import com.solidbrain.services.DealService;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Created by Krzysztof Wilk on 06/09/16.
 */
@Component
@Slf4j
class WorkflowTask {
    private Sync sync;

    private DealService dealService;
    private ContactService contactService;

    @Autowired
    public WorkflowTask(Sync sync,
                        ContactService contactService,
                        DealService dealService) {

        this.sync = sync;
        this.contactService = contactService;
        this.dealService = dealService;
    }

    /**
     * Main workflow loop
     */
    @Scheduled(fixedDelayString = "${workflow.loop.interval}")
    public void runWorkflow() {
        log.info("Starting workflow run");

        // Workaround: https://gist.github.com/michal-mally/73ea265718a0d29aac350dd81528414f
        sync.subscribe(Account.class, (meta, account) -> true)
                .subscribe(Address.class, (meta, address) -> true)
                .subscribe(AssociatedContact.class, (meta, associatedContact) -> true)
                .subscribe(Contact.class, (meta, contact) ->  processContact(meta.getSync().getEventType(), contact))
                .subscribe(Deal.class, (meta, deal) -> processDeal(meta.getSync().getEventType(), deal))
                .subscribe(LossReason.class, (meta, lossReason) -> true)
                .subscribe(Note.class, (meta, note) -> true)
                .subscribe(Pipeline.class, (meta, pipeline) -> true)
                .subscribe(Source.class, (meta, source) -> true)
                .subscribe(Stage.class, (meta, stage) -> true)
                .subscribe(Tag.class, (meta, tag) -> true)
                .subscribe(Task.class, (meta, task) -> true)
                .subscribe(User.class, (meta, user) -> true)
                .subscribe(Lead.class, (meta, lead) -> true)
                .fetch();
    }

    boolean processContact(String eventType, Contact contact) {
        return contactService.processContact(eventType, contact);
    }

    boolean processDeal(String eventType, Deal deal) {
        return dealService.processDeal(eventType, deal);
    }
}
