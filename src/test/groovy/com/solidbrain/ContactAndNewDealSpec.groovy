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

import static java.util.concurrent.TimeUnit.MILLISECONDS
import static java.util.concurrent.TimeUnit.SECONDS
import static org.awaitility.Awaitility.await


/**
 * Created by Krzysztof Wilk on 05/09/16.
 */

@Slf4j
class ContactAndNewDealSpec extends Specification {
    Client baseClient

    def sampleSalesRepId
    def sampleAccountManagerId

    long waitForWorkflowExecutionTimeout = 30_000
    long awaitPollingInterval = 1_000
    long postDeleteTimeout = 60_000

    def setup() {
        assert accessToken

        baseClient = new Client(new Configuration.Builder()
                .accessToken(accessToken)
                .build())

        sampleSalesRepId = getSampleUserId("salesrep")
        assert sampleSalesRepId

        sampleAccountManagerId = getSampleUserId("accountmanager")
        assert sampleAccountManagerId
    }

    def getAccessToken() {
        return System.getProperty("BASE_CRM_TOKEN", System.getenv("BASE_CRM_TOKEN"))
    }

    def cleanup() {
        def sampleCompanyId = baseClient.contacts().list([name : sampleCompanyName])[0]?.id

        if (sampleCompanyId) {
            def dealIds = baseClient.deals().list([contact_id : sampleCompanyId])*.id
            if (dealIds) {
                dealIds.each { id -> baseClient.deals().delete(id) }
                await().atMost(postDeleteTimeout, MILLISECONDS).pollInterval(1, SECONDS).until {
                    baseClient.deals().list([contact_id : sampleCompanyId]).isEmpty()
                }
            }

            baseClient.contacts().delete(sampleCompanyId)
            await().atMost(postDeleteTimeout, MILLISECONDS).pollInterval(1, SECONDS).until {
                baseClient.contacts().list([name : sampleCompanyName]).isEmpty()
            }
        }
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
        when: "new contact that is a company owned by a sales rep is created"
        Contact sampleContact = baseClient.contacts().create([name : sampleCompanyName,
                                                              is_organization:  true,
                                                              owner_id: sampleSalesRepId])

        then: "new deal at the first stage of the pipeline for this company is created"
        await().atMost(waitForWorkflowExecutionTimeout, MILLISECONDS).pollInterval(awaitPollingInterval, MILLISECONDS).until {
            !baseClient.deals().list([contact_id: sampleContact.id]).isEmpty()
        }
        Deal sampleDeal = baseClient.deals().list([contact_id: sampleContact.id]).get(0)
        sampleDeal.name == getSampleDealName(sampleCompanyName)
        sampleDeal.ownerId == sampleContact.ownerId
        sampleDeal.stageId == firstStageId
    }

    def "should not create deal if the newly created contact is not a company"() {
        when: "new contact that is not a company is created"
        Contact sampleContact = baseClient.contacts().create([name : sampleCompanyName,
                                                              is_organization:  false])

        then: "no new deal is created"
        sleep(waitForWorkflowExecutionTimeout)
        !baseClient.deals().list([contact_id: sampleContact.id])
    }

    def "should not create deal if the owner of the newly created contact is not a sales representative"() {
        when: "new contact that is a company that is not owned by a sales rep is created"
        Contact sampleContact = baseClient.contacts().create([name : sampleCompanyName,
                                                              is_organization:  true,
                                                              owner_id: sampleAccountManagerId])

        then: "no new deal is created"
        sleep(waitForWorkflowExecutionTimeout)
        !baseClient.deals().list([contact_id: sampleContact.id])
    }

    def "should not create deal if the newly created contact does not have an owner"() {
        when: "new contact that is a company but does not have an owner is created"
        Contact sampleContact = baseClient.contacts().create([name : sampleCompanyName,
                                                              is_organization:  true])

        then: "no new deal is created"
        sleep(waitForWorkflowExecutionTimeout)
        !baseClient.deals().list([contact_id: sampleContact.id])
    }
}
