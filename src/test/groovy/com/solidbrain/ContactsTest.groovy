package com.solidbrain

import com.getbase.Client
import com.getbase.models.Contact
import com.getbase.Configuration
import com.getbase.models.Deal
import com.getbase.models.Stage
import com.getbase.services.StagesService
import com.getbase.services.UsersService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ContextConfiguration
import org.springframework.web.context.WebApplicationContext
import spock.lang.Specification
import spock.lang.Shared

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter


/**
 * Created by Krzysztof Wilk on 05/09/16.
 */

@ContextConfiguration  // makes Spock to start Spring context
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ContactsTest extends Specification {

    @Autowired
    WebApplicationContext context

    @Shared Client baseClient

    Contact sampleContact

    Deal sampleDeal

    def setupSpec() {
        assert accessToken
        baseClient = new Client(new Configuration.Builder()
                .accessToken(accessToken)
                .verbose()
                .build())
    }

    def getAccessToken() {
        return System.getenv("BASE_CRM_TOKEN")
    }


    // Create sample contact if not exists yet
    // Create sample deal and set its primary contact to newly created sample contact
    def setup() {
        def sampleContactId = baseClient.contacts().list([name : sampleCompanyName])[0]?.id

        if (sampleContactId) {
            def sampleDealName = getSampleDealName(sampleCompanyName)
            def sampleDealId = baseClient.deals().list([name : sampleDealName])[0]?.id
            if (sampleDealId) {
                baseClient.deals().delete(sampleDealId)
            }

            baseClient.contacts().delete(sampleContactId)
        }

        sampleContact = baseClient.contacts().create([name : sampleCompanyName,
                                                        is_organization : true,
                                                        website : sampleCompanyWebsite,
                                                        owner_id: sampleCompanyOwnerId])

        sampleContactId = sampleContact.id

        sampleDeal = baseClient.deals().create([name : getSampleDealName(sampleCompanyName),
                                                contact_id : sampleContactId,
                                                owner_id : sampleContact.ownerId,
                                                stage_id: firstStageId])
    }

    def getSampleCompanyName() {
        return "Some Company Of Mine"
    }

    def getSampleCompanyWebsite() {
        return "http://www.somecompanyofmine.com"
    }

    def getSampleDealName(String contactName) {
        return contactName + " " + ZonedDateTime.now().toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE)
    }

    def getFirstStageId() {
        return baseClient.stages().list(new StagesService.SearchCriteria().name("Incoming"))[0]?.id
    }

    // Find first sales representative and make him/her an owner of the sample company (in CRM only)
    def getSampleCompanyOwnerId() {
        def salesRepEmailsPattern = "\\+salesrep\\+"

        return baseClient.users().list(new UsersService.SearchCriteria().
                                                    asMap()).
                            find { it.confirmed &&
                                    it.status == "active" &&
                                    it.role == "user" &&
                                    it.email =~ salesRepEmailsPattern}.
                            id
    }

    def "sample contact exists"() {
        given:
        context != null
        baseClient instanceof Client

        when:
        sampleContact instanceof Contact

        then:
        sampleContact.name == sampleCompanyName
        sampleContact.isOrganization
        sampleContact.website == sampleCompanyWebsite
        sampleContact.ownerId
    }

    def "sample deal attached to sample contact exists"() {
        given:
        context != null
        baseClient instanceof Client

        when:
        sampleContact instanceof Contact
        sampleDeal instanceof Deal

        then:
        sampleDeal.contactId == sampleContact.id
        sampleDeal.name == getSampleDealName(sampleCompanyName)
        sampleDeal.ownerId == sampleContact.ownerId
        sampleDeal.stageId == firstStageId
    }
}
