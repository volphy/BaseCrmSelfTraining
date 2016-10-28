package com.solidbrain.workflow

import com.getbase.Client
import com.getbase.models.Contact
import com.getbase.models.Deal
import com.getbase.models.Stage
import com.getbase.models.User
import com.getbase.services.ContactsService
import com.getbase.services.DealsService
import com.getbase.services.StagesService
import com.getbase.services.UsersService
import com.getbase.sync.Sync
import com.solidbrain.services.ContactService
import com.solidbrain.services.DealService
import groovy.util.logging.Slf4j
import spock.lang.IgnoreIf
import spock.lang.Shared
import spock.lang.Specification

/**
 * Created by Krzysztof Wilk on 06/10/2016.
 */
@Slf4j
@IgnoreIf({ properties["integrationTest"] == "true" })
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

    def "should create deal if the newly created contact is a company and the owner of the newly created contact is a sales representative"() {
        given:
        def task = new WorkflowTask()
        def client = Stub(Client)
        def sync = Stub(Sync)
        task.initialize(client, sync, dealNameDateFormat, accountManagersEmails, accountManagerOnDutyEmail, salesRepresentativesEmails)

        and:
        def contact = getSampleContact(isOrganization:  true)
        def owner = getSampleContactsOwner(email: salesRepresentativesEmails[0])

        and:
        def usersService = Stub(UsersService)
        usersService.get(owner.id) >> owner
        client.users() >> usersService
        def stagesService = Stub(StagesService)
        stagesService.list(!null) >> []
        client.stages() >> stagesService
        def dealsService = Mock(DealsService)
        dealsService.list(!null) >> []
        client.deals() >> dealsService

        when:
        task.processContact(eventType, contact)

        then:
        1 * dealsService.create({ d -> d.name.startsWith(contact.name) && d.contactId == contact.id })

        where:
        eventType << ["created", "updated"]
    }

    def "should not create deal if contact does not meet criteria"() {
        given:
        def task = new WorkflowTask()
        def client = Stub(Client)
        def sync = Stub(Sync)
        task.initialize(client, sync, dealNameDateFormat, accountManagersEmails, accountManagerOnDutyEmail, salesRepresentativesEmails)

        and:
        def contact = getSampleContact(isOrganization: isOrganization)
        def owner = getSampleContactsOwner(email: ownersEmail)

        and:
        def usersService = Stub(UsersService)
        usersService.get(owner.id) >> owner
        client.users() >> usersService
        def stagesService = Stub(StagesService)
        stagesService.list(!null) >> []
        client.stages() >> stagesService
        def dealsService = Mock(DealsService)
        dealsService.list(!null) >> []
        client.deals() >> dealsService

        when:
        task.processContact(eventType, contact)

        then:
        0 * dealsService.create(_)

        where:
        isOrganization  | ownersEmail                       | eventType
        true            | salesRepresentativesEmails[0]     | "non-existing"
        false           | salesRepresentativesEmails[0]     | "created"
        false           | salesRepresentativesEmails[0]     | "updated"
        false           | salesRepresentativesEmails[0]     | "non-existing"
        true            | accountManagersEmails[0]          | "created"
        true            | accountManagersEmails[0]          | "updated"
        true            | accountManagersEmails[0]          | "non-existing"
        false           | accountManagersEmails[0]          | "created"
        false           | accountManagersEmails[0]          | "updated"
        false           | accountManagersEmails[0]          | "non-existing"
        true            | sampleOtherUserEmail              | "created"
        true            | sampleOtherUserEmail              | "updated"
        true            | sampleOtherUserEmail              | "non-existing"
        false           | sampleOtherUserEmail              | "created"
        false           | sampleOtherUserEmail              | "updated"
        false           | sampleOtherUserEmail              | "non-existing"
    }

    def getSampleOtherUserEmail() {
        "some+admin@gmail.com"
    }

    def "should assign contact related to won deal to account manager on duty"() {
        given:
        def task = new WorkflowTask()
        def client = Stub(Client)
        def sync = Stub(Sync)
        task.initialize(client, sync, dealNameDateFormat, accountManagersEmails, accountManagerOnDutyEmail, salesRepresentativesEmails)

        and:
        def contact = getSampleContact(isOrganization: true)
        def deal = getSampleDeal()
        def owner = getSampleContactsOwner(email: salesRepresentativesEmails[0])
        def wonStage = getSampleStage(category: "won")

        and:
        def usersService = Stub(UsersService)
        usersService.get(owner.id) >> owner
        usersService.list(_ as UsersService.SearchCriteria) >> [owner]
        client.users() >> usersService
        def stagesService = Stub(StagesService)
        stagesService.list(!null) >> [wonStage]
        client.stages() >> stagesService
        def dealsService = Stub(DealsService)
        dealsService.list(!null) >> []
        client.deals() >> dealsService
        def contactsService = Mock(ContactsService)
        contactsService.get(contact.id) >> contact
        contactsService.update(_ as Long, _ as Map<String, Object>) >> []
        client.contacts() >> contactsService

        when:
        task.processDeal(eventType, deal)

        then:
        1 * contactsService.update(contact.id, {attr -> attr["owner_id"] == owner.id })

        where:
        eventType << ["created", "updated"]
    }

    def getSampleStage(Map parameters) {
        new Stage(id: 1L, category: parameters.category)
    }

    def "should not assign contact related to won deal to account manager on duty"() {
        given:
        def task = new WorkflowTask()
        def client = Stub(Client)
        def sync = Stub(Sync)
        task.initialize(client, sync, dealNameDateFormat, accountManagersEmails, accountManagerOnDutyEmail, salesRepresentativesEmails)

        and:
        def contact = getSampleContact(isOrganization: true)
        def deal = getSampleDeal()
        def owner = getSampleContactsOwner(email: ownersEmail)
        def wonStage = getSampleStage(category: stageCategory)

        and:
        def usersService = Stub(UsersService)
        usersService.get(owner.id) >> owner
        usersService.list(_ as UsersService.SearchCriteria) >> [owner]
        client.users() >> usersService
        def stagesService = Stub(StagesService)
        stagesService.list(!null) >> [wonStage]
        client.stages() >> stagesService
        def dealsService = Stub(DealsService)
        dealsService.list(!null) >> [deal]
        client.deals() >> dealsService
        def contactsService = Mock(ContactsService)
        contactsService.get(contact.id) >> contact
        contactsService.update(_ as Long, _ as Map<String, Object>) >> []
        client.contacts() >> contactsService

        when:
        task.processDeal(eventType, deal)

        then:
        0 * contactsService.update(_, _)

        where:
        stageCategory   | ownersEmail                       | eventType
        "won"           | accountManagersEmails[0]          | "created"
        "won"           | accountManagersEmails[0]          | "updated"
        "won"           | accountManagersEmails[0]          | "non-existing"
        "invalid"       | salesRepresentativesEmails[0]     | "created"
        "invalid"       | salesRepresentativesEmails[0]     | "updated"
        "invalid"       | salesRepresentativesEmails[0]     | "non-existing"
        "invalid"       | accountManagersEmails[0]          | "created"
        "invalid"       | accountManagersEmails[0]          | "updated"
        "invalid"       | accountManagersEmails[0]          | "non-existing"
        "invalid"       | sampleOtherUserEmail              | "created"
        "invalid"       | sampleOtherUserEmail              | "updated"
        "invalid"       | sampleOtherUserEmail              | "non-existing"
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

    def "should fail if processing contact throws exception"() {
        given:
        def task = new WorkflowTask()
        def client = Stub(Client)
        def sync = Stub(Sync)
        task.initialize(client, sync, dealNameDateFormat, accountManagersEmails, accountManagerOnDutyEmail, salesRepresentativesEmails)

        and:
        def contact = getSampleContact(isOrganization: true)

        and:
        def usersService = Stub(UsersService)
        usersService.get(_) >> { throw new Exception("Cannot process contact (id=" + contact.id + ". Message=null")}
        client.users() >> usersService

        when:
        def status = task.processContact(eventType, contact)

        then:
        !status

        where:
        eventType << ["created", "updated"]
    }

    def "should fail if processing deal throws exception"() {
        given:
        def task = new WorkflowTask()
        def client = Stub(Client)
        def sync = Stub(Sync)
        task.initialize(client, sync, dealNameDateFormat, accountManagersEmails, accountManagerOnDutyEmail, salesRepresentativesEmails)

        and:
        def deal = getSampleDeal()

        and:
        def stagesService = Stub(StagesService)
        stagesService.list(_) >> { throw new Exception("Cannot process deal (id=" + deal.id + ". Message=null")}
        client.stages() >> stagesService

        when:
        def status = task.processDeal(eventType, deal)

        then:
        !status

        where:
        eventType << ["created", "updated"]
    }

    def "should use default deal name suffix if invalid date format specified"() {
        given:
        def client = Stub(Client)
        def dealsService = Mock(DealsService)
        client.deals() >> dealsService
        def contactService = Stub(ContactService)
        def dealService = new DealService(client, "INVALID-FORMAT", [], contactService)
        def contact = getSampleContact(isOrganization: true)

        when:
        dealService.createNewDeal(contact)

        then:
        1 * dealsService.create( { d -> d.name =~ /\d{4}-\d{2}-\d{2}$/ } )
    }
}
