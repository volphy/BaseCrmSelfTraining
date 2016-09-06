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
import java.util.concurrent.TimeUnit

import static org.awaitility.Awaitility.await
import static java.util.concurrent.TimeUnit.SECONDS



/**
 * Created by Krzysztof Wilk on 05/09/16.
 */

@ContextConfiguration  // makes Spock start Spring context
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ContactsTest extends Specification {

    @Autowired
    WebApplicationContext context

    @Shared Client baseClient

    def setupSpec() {
        assert accessToken

        baseClient = new Client(new Configuration.Builder()
                .accessToken(accessToken)
                .verbose()
                .build())

        assert baseClient instanceof Client
    }

    def getAccessToken() {
        return System.getenv("BASE_CRM_TOKEN")
    }

    def cleanup() {
        def sampleCompanyId = baseClient.contacts().list([name : sampleCompanyName])[0]?.id

        if (sampleCompanyId) {
            def sampleDealId = baseClient.deals().list([contact_id : sampleCompanyId])[0]?.id
            if (sampleDealId) {
                baseClient.deals().delete(sampleDealId)
            }
        }

        baseClient.contacts().delete(sampleCompanyId)
    }


    def getSampleCompanyName() {
        return "Some Company Of Mine"
    }

    def getSampleDealName(String contactName) {
        return contactName + " " + ZonedDateTime.now().toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE)
    }

    def getFirstStageId() {
        return baseClient.stages().list(new StagesService.SearchCriteria().name("Incoming"))[0]?.id
    }

    def getSampleSalesRepresentativeId() {
        def salesRepEmailsPattern = "\\+salesrep\\+"

        return baseClient.users().list(new UsersService.SearchCriteria().
                                                    asMap()).
                            find { it.confirmed &&
                                    it.status == "active" &&
                                    it.role == "user" &&
                                    it.email =~ salesRepEmailsPattern}.
                            id
    }

    def getSampleAccountManagerId() {
        def accountManagerEmailsPattern = "\\+accountmanager\\+"

        return baseClient.users().list(new UsersService.SearchCriteria().
                asMap()).
                find { it.confirmed &&
                        it.status == "active" &&
                        it.role == "user" &&
                        it.email =~ accountManagerEmailsPattern}.
                id
    }

    def "should create deal if the newly created contact is a company and the owner of the newly created contact is a sales representative"() {
        given:
        sampleCompanyName
        def isContactACompany = true
        sampleSalesRepresentativeId

        when:
        Contact sampleContact = baseClient.contacts().create([name : sampleCompanyName,
                                                              is_organization:  isContactACompany,
                                                              owner_id: sampleSalesRepresentativeId])
        Deal sampleDeal = null
        await().atMost(30, SECONDS).until {
            sampleDeal = baseClient.deals().list([contact_id: sampleContact.id])[0]
        }

        then:
        sampleDeal
        sampleDeal.name == getSampleDealName(sampleCompanyName)
        sampleDeal.ownerId == sampleContact.ownerId
        sampleDeal.stageId == firstStageId
    }

    def "should not create deal if the newly created contact is not a company"() {
        given:
        sampleCompanyName
        def isContactACompany = false
        sampleSalesRepresentativeId

        when:
        Contact sampleContact = baseClient.contacts().create([name : sampleCompanyName,
                                                              is_organization:  isContactACompany,
                                                              owner_id: sampleSalesRepresentativeId])
        Deal sampleDeal = null
        await().atMost(30, SECONDS).until {
            sampleDeal = baseClient.deals().list([contact_id: sampleContact.id])[0]
        }

        then:
        sampleDeal == null
    }

    def "should not create deal if the owner of the newly created contact is not a sales representative"() {
        given:
        sampleCompanyName
        def isContactACompany = true
        sampleAccountManagerId

        when:
        Contact sampleContact = baseClient.contacts().create([name : sampleCompanyName,
                                                              is_organization:  isContactACompany,
                                                              owner_id: sampleAccountManagerId])
        Deal sampleDeal = null
        await().atMost(30, SECONDS).until {
            sampleDeal = baseClient.deals().list([contact_id: sampleContact.id])[0]
        }

        then:
        sampleDeal == null
    }

    def "should not create deal if the newly created contact does not have an owner"() {
        given:
        sampleCompanyName
        def isContactACompany = true

        when:
        Contact sampleContact = baseClient.contacts().create([name : sampleCompanyName,
                                                              is_organization:  isContactACompany])
        Deal sampleDeal = null
        await().atMost(30, SECONDS).until {
            sampleDeal = baseClient.deals().list([contact_id: sampleContact.id])[0]
        }

        then:
        sampleDeal == null
    }
}
