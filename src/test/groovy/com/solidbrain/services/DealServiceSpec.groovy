package com.solidbrain.services

import com.getbase.Client
import com.getbase.services.ContactsService
import com.getbase.services.DealsService
import com.getbase.services.StagesService
import com.getbase.services.UsersService
import spock.lang.IgnoreIf

/**
 * Created by Krzysztof Wilk on 31/10/2016.
 */
@IgnoreIf({ properties["integrationTest"] == "true" })
class DealServiceSpec extends AbstractSpec {

    def "should assign contact related to won deal to account manager on duty"() {
        given:
        def client = Stub(Client)
        def userService = new UserService(client)
        def dealService = new DealService(client, accountManagersEmails, accountManagerOnDutyEmail, userService)

        and:
        def contact = getSampleContact(isOrganization: true)
        def deal = getSampleDeal()
        def owner = getSampleContactsOwner(email: salesRepresentativesEmails[0])
        def wonStage = getSampleStage(category: "won")
        def accountManagerOnDuty = getSampleContactsOwner(email: accountManagerOnDutyEmail)

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
        dealService.processDeal(eventType, deal)

        then:
        1 * contactsService.update(contact.id, {attr -> attr["owner_id"] == accountManagerOnDuty.id })

        where:
        eventType << ["created", "updated"]
    }


    def "should not assign contact related to won deal to account manager on duty"() {
        given:
        def client = Stub(Client)
        def userService = new UserService(client)
        def dealService = new DealService(client, accountManagersEmails, accountManagerOnDutyEmail, userService)

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
        dealService.processDeal(eventType, deal)

        then:
        0 * contactsService.update(_, _)

        where:
        stageCategory   | ownersEmail                       | eventType
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
}
