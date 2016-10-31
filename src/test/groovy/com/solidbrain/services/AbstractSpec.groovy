package com.solidbrain.services

import com.getbase.models.Contact
import com.getbase.models.Deal
import com.getbase.models.Stage
import com.getbase.models.User
import groovy.util.logging.Slf4j
import spock.lang.Shared
import spock.lang.Specification

/**
 * Created by Krzysztof Wilk on 31/10/2016.
 */
@Slf4j
abstract class AbstractSpec extends Specification {

    @Shared List<String> accountManagersEmails
    @Shared String accountManagerOnDutyEmail
    @Shared List<String> salesRepresentativesEmails

    @Shared def dealNameDateFormat

    def getAccessToken() {
        def token = System.getProperty("BASE_CRM_TOKEN", System.getenv("BASE_CRM_TOKEN"))
        assert token
        return token
    }


    def setupSpec() {
        def file = new File("src/test/resources/test.properties")

        file.eachLine { l ->
            if (l.startsWith("workflow.deal.name.date.format")) {
                dealNameDateFormat = l.split("=")[1]
            }
        }

        assert dealNameDateFormat
        log.debug("dealNameDateFormat={}", dealNameDateFormat)

        accountManagersEmails = System.getProperty("workflow.account.managers.emails",
                System.getenv("workflow_account_managers_emails"))?.split("\\s*,\\s*")
        assert !accountManagersEmails.isEmpty()
        log.debug("accountManagersEmails={}", accountManagersEmails)

        accountManagerOnDutyEmail = System.getProperty("workflow.account.manager.on.duty.email",
                System.getenv("workflow_account_manager_on_duty_email"))
        assert accountManagerOnDutyEmail
        log.debug("accountManagerOnDuty={}", accountManagerOnDutyEmail)

        salesRepresentativesEmails = System.getProperty("workflow.sales.representatives.emails",
                System.getenv("workflow_sales_representatives_emails"))?.split("\\s*,\\s*")
        assert !salesRepresentativesEmails.isEmpty()
        log.debug("salesRepresentativesEmails={}", salesRepresentativesEmails)
    }

    def getSampleStage(Map parameters) {
        new Stage(id: 1L, category: parameters.category)
    }

    def getSampleContactsOwner(Map parameters) {
        new User(id: 465L,
                email: parameters.email)
    }

    def getSampleDeal() {
        new Deal(id: 567L,
                name: "Sample Company of Mine 2016-10-07",
                contactId: 123L,
                ownerId: 465L,
                stageId: 1L)
    }

    def getSampleContact(Map parameters) {
        new Contact(id: 123L,
                name: "Sample Company of Mine",
                isOrganization: parameters.isOrganization,
                ownerId: 465L)
    }

    def getSampleOtherUserEmail() {
        "some+admin@gmail.com"
    }
}
