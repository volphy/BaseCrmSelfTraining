package com.solidbrain

import com.getbase.Client
import com.getbase.models.Contact
import com.getbase.models.Deal
import com.getbase.models.User
import com.getbase.services.ContactsService
import com.getbase.services.DealsService
import com.getbase.services.StagesService
import com.getbase.services.UsersService
import com.getbase.sync.Sync
import groovy.util.logging.Slf4j
import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Specification

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Created by Krzysztof Wilk on 06/10/2016.
 */
@Slf4j
// TODO - extend common part of WorkflowSpec and ContactAndNewDealSpec into abstract class if it makes any sense
class WorkflowSpec extends Specification {

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


    def getSampleCompanyName() {
        "Some Company Of Mine"
    }

    def getSampleDealName(String contactName) {
        contactName + " " + ZonedDateTime.now().toLocalDate().format(DateTimeFormatter.ofPattern(dealNameDateFormat))
    }


    def "should create deal if the newly created contact is a company and the owner of the newly created contact is a sales representative"() {
        given:
        def task = new WorkflowTask()
        def client = Mock(Client)
        def sync = Mock(Sync)
        task.initialize(client, sync, dealNameDateFormat, accountManagersEmails, accountManagerOnDutyEmail, salesRepresentativesEmails)
        def contact = new Contact(id: 123L,
                                    name: "Sample Company of Mine",
                                    isOrganization: true,
                                    ownerId: 465L)
        def owner = new User(id: 465L,
                                email: "chrismwilk+salesrep+lisa@gmail.com")
        def eventType = "created"
        def usersService = Mock(UsersService)
        usersService.get(465L) >> owner
        client.users() >> usersService
        def stagesService = Mock(StagesService)
        stagesService.list(!null) >> []
        client.stages() >> stagesService
        def dealsService = Mock(DealsService)
        dealsService.list(!null) >> []
        client.deals() >> dealsService

        when:
        task.processContact(eventType, contact)

        then:
        1 * dealsService.create({ d -> d.name.startsWith(contact.name) && d.contactId == contact.id })
    }

    @Ignore
    def "should assign contact related to won deal to account manager on duty"() {
        given:
        def task = new WorkflowTask()
        def client = Mock(Client)
        def sync = Mock(Sync)
        task.initialize(client, sync, dealNameDateFormat, accountManagersEmails, accountManagerOnDutyEmail, salesRepresentativesEmails)
        def contact = new Contact(id: 123L,
                name: "Sample Company of Mine",
                isOrganization: true,
                ownerId: 465L)
        def deal = new Deal(id: 567L,
                                name: "Sample Company of Mine 2016-10-07",
                                contactId: 123L,
                                ownerId: 765L,
                                stageId: 1L)
        def owner = new User(id: 465L,
                email: "chrismwilk+salesrep+lisa@gmail.com")
        def eventType = "created"
        def usersService = Mock(UsersService)
        usersService.get(465L) >> owner
        usersService.list(_ as UsersService.SearchCriteria) >> [123L]
        client.users() >> usersService
        def stagesService = Mock(StagesService)
        stagesService.list(!null) >> []
        client.stages() >> stagesService
        def dealsService = Mock(DealsService)
        dealsService.list(!null) >> []
        client.deals() >> dealsService
        def contactsService = Mock(ContactsService)
        contactsService.update(_ as Long, _ as Map<String, Object>) >> []
        client.contacts() >> contactsService

        when:
        task.processDeal(eventType, deal)

        then:
        1 * contactsService.update(_, _)
    }
}
