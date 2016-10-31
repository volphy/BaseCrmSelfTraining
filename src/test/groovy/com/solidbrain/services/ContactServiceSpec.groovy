package com.solidbrain.services

import com.getbase.Client
import com.getbase.services.DealsService
import com.getbase.services.StagesService
import com.getbase.services.UsersService
import groovy.util.logging.Slf4j
import spock.lang.IgnoreIf

/**
 * Created by Krzysztof Wilk on 06/10/2016.
 */
@Slf4j
@IgnoreIf({ properties["integrationTest"] == "true" })

class ContactServiceSpec extends AbstractSpec {

    def "should create deal if the newly created contact is a company and the owner of the newly created contact is a sales representative"() {
        given:
        def client = Stub(Client)
        def userService = new UserService(client)
        def contactService = new ContactService(client, dealNameDateFormat, salesRepresentativesEmails, userService)

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
        contactService.processContact(eventType, contact)

        then:
        1 * dealsService.create({ d -> d.name.startsWith(contact.name) && d.contactId == contact.id })

        where:
        eventType << ["created", "updated"]
    }


    def "should not create deal if contact does not meet criteria"() {
        given:
        def client = Stub(Client)
        def userService = new UserService(client)
        def contactService = new ContactService(client, dealNameDateFormat, salesRepresentativesEmails, userService)

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
        contactService.processContact(eventType, contact)

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


    def "should fail if processing contact throws exception"() {
        given:
        def client = Stub(Client)
        def userService = new UserService(client)
        def contactService = new ContactService(client, dealNameDateFormat, salesRepresentativesEmails, userService)

        and:
        def contact = getSampleContact(isOrganization: true)

        and:
        def usersService = Stub(UsersService)
        usersService.get(_) >> { throw new Exception("Cannot process contact (id=" + contact.id + ". Message=null")}
        client.users() >> usersService

        when:
        def status = contactService.processContact(eventType, contact)

        then:
        !status

        where:
        eventType << ["created", "updated"]
    }


    def "should fail if processing deal throws exception"() {
        given:
        def client = Stub(Client)
        def userService = Stub(UserService)
        def dealService = new DealService(client, accountManagersEmails, accountManagerOnDutyEmail, userService)

        and:
        def deal = getSampleDeal()

        and:
        def stagesService = Stub(StagesService)
        stagesService.list(_) >> { throw new Exception("Cannot process deal (id=" + deal.id + ". Message=null")}
        client.stages() >> stagesService

        when:
        def status = dealService.processDeal(eventType, deal)

        then:
        !status

        where:
        eventType << ["created", "updated"]
    }


    def "should use default deal name suffix if invalid date format specified"() {
        given:
        def client = Stub(Client)
        def userService = Stub(UserService)
        def contactService = new ContactService(client, "INVALID-FORMAT", salesRepresentativesEmails, userService)

        and:
        def dealsService = Mock(DealsService)
        client.deals() >> dealsService
        def contact = getSampleContact(isOrganization: true)

        when:
        contactService.createNewDeal(contact)

        then:
        1 * dealsService.create( { d -> d.name =~ /\d{4}-\d{2}-\d{2}$/ } )
    }
}
