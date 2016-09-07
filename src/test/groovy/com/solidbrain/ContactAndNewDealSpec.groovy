package com.solidbrain

import com.getbase.Client
import com.getbase.models.Contact
import com.getbase.Configuration
import com.getbase.models.Deal
import com.getbase.services.StagesService
import com.getbase.services.UsersService
import groovy.util.logging.Slf4j
import spock.lang.Specification

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

import static org.awaitility.Awaitility.await
import static java.util.concurrent.TimeUnit.SECONDS



/**
 * Created by Krzysztof Wilk on 05/09/16.
 */

@Slf4j
class ContactAndNewDealSpec extends Specification {
    Client baseClient

    def setup() {
        assert accessToken

        baseClient = new Client(new Configuration.Builder()
                .accessToken(accessToken)
                .build())

        assert baseClient instanceof Client
    }

    def getAccessToken() {
        return System.getProperty("BASE_CRM_TOKEN", System.getenv("BASE_CRM_TOKEN"))
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

    // Find id of the first available user from a given group
    def getSampleUserId(String groupName) {
        def userEmailsPattern = "\\+" + groupName + "\\+"

        return baseClient.users().list(new UsersService.SearchCriteria()).
                find { it.confirmed &&
                        it.status == "active" &&
                        it.role == "user" &&
                        it.email =~ userEmailsPattern}.
                id
    }

    def "should create deal if the newly created contact is a company and the owner of the newly created contact is a sales representative"() {
        given:
        def userId = getSampleUserId("salesrep")

        when:
        Contact sampleContact = baseClient.contacts().create([name : sampleCompanyName,
                                                              is_organization:  true,
                                                              owner_id: userId])

        then:
        await().atMost(3, SECONDS).pollInterval(1, SECONDS).ignoreExceptions().until {
            !baseClient.deals().list([contact_id: sampleContact.id]).isEmpty()
        }
        Deal sampleDeal = baseClient.deals().list([contact_id: sampleContact.id]).get(0)
        sampleDeal.name == getSampleDealName(sampleCompanyName)
        sampleDeal.ownerId == sampleContact.ownerId
        sampleDeal.stageId == firstStageId
    }

    def "should not create deal if the newly created contact is not a company"() {
        when:
        Contact sampleContact = baseClient.contacts().create([name : sampleCompanyName,
                                                              is_organization:  false])

        then:
        sleep(30_000)
        baseClient.deals().list([contact_id: sampleContact.id]).isEmpty()
    }

    def "should not create deal if the owner of the newly created contact is not a sales representative"() {
        given:
        def userId = getSampleUserId("accountmanager")

        when:
        Contact sampleContact = baseClient.contacts().create([name : sampleCompanyName,
                                                              is_organization:  true,
                                                              owner_id: userId])

        then:
        sleep(30_000)
        baseClient.deals().list([contact_id: sampleContact.id]).isEmpty()
    }

    def "should not create deal if the newly created contact does not have an owner"() {
        when:
        Contact sampleContact = baseClient.contacts().create([name : sampleCompanyName,
                                                              is_organization:  true])

        then:
        sleep(30_000)
        baseClient.deals().list([contact_id: sampleContact.id])isEmpty()
    }
}
