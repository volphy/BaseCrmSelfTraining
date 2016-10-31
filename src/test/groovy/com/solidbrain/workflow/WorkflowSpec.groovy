package com.solidbrain.workflow

import com.getbase.models.Contact

import com.getbase.models.Deal

import groovy.util.logging.Slf4j
import spock.lang.Narrative
import spock.lang.Requires

import spock.lang.Stepwise
import spock.lang.Subject
import spock.lang.Title
import spock.lang.Unroll

import static java.util.concurrent.TimeUnit.MILLISECONDS

import static org.awaitility.Awaitility.await


/**
 * Created by Krzysztof Wilk on 05/09/16.
 */

@Requires({ properties["integrationTest"] == "true" })
@Narrative('''Spring Boot based workflow creates new Deal if newly created Contact meets
the following requirements:
1. Contact is a company (organization)
2. Contact's owner is a Sales Representative
3. there are no active Deals assigned to this Contact
Later that Contact is assigned to a given Account Manager (here: account manager on duty)
if it meets the following requirements:
1. Deal that is assigned to this Contact is Won (its stage is Won)
2. Contact is assigned to a user who is not an Account Manager''')
@Title("Integration test for deal created by workflow")
@Unroll("""Create contact and deal test:
isOrganization=#isOrganization, ownerId=#ownerId, dealName=#dealName, dealOwnerId=#dealOwnerId""")
@Subject(WorkflowTask)
@Stepwise
@Slf4j
class WorkflowSpec extends AbstractSpec {

    def "should create deal (correct contact's attributes)"() {
        log.debug("Testing positive path (test-to-pass)")

        when: "new contact that is a company owned by a sales rep is created"
        Contact sampleContact = createSampleContact(getSampleCompanyName(), isOrganization, ownerId)

        then: "new deal at the first stage of the pipeline for this company is created"
        await().atMost(waitForWorkflowExecutionTimeout, MILLISECONDS)
                .pollInterval(awaitPollingInterval, MILLISECONDS).until {
            baseClient.contacts().get(sampleContact.id) && !baseClient.deals().list([contact_id: sampleContact.id]).isEmpty()
        }
        Deal sampleDeal = baseClient.deals().list([contact_id: sampleContact.id])[0]
        sampleDeal
        sampleDeal.with {
            name == getSampleDealName(getSampleCompanyName())
            ownerId == dealOwnerId
            // dealStageId cannot be moved to where: because it is evaluated after test's completion
            stageId == getFirstStageId(sampleDeal)
        }

        when: "newly created deal is moved to won stage"
        def wonStageId = getWonStageId(sampleDeal)
        baseClient.deals().update(sampleDeal.id, ["stage_id": wonStageId])

        then: "deal stage has been changed"
        await().atMost(waitForWorkflowExecutionTimeout, MILLISECONDS)
                .pollInterval(awaitPollingInterval, MILLISECONDS)
                .until {
            baseClient.deals().get(sampleDeal.id).getStageId().equals(wonStageId)
        }

        and: "contact has been assigned to an account manager"
        await().atMost(waitForWorkflowExecutionTimeout, MILLISECONDS)
                .pollInterval(awaitPollingInterval, MILLISECONDS)
                .until {
            allAccountManagersIds.contains(baseClient.contacts().get(sampleContact.id).getOwnerId())
        }

        where: "sample contact's attributes are"
        isOrganization << [true]
        ownerId << [sampleSalesRepId]
        dealName << [getSampleDealName(sampleCompanyName)]
        dealOwnerId << [sampleSalesRepId]
    }



    def "should not create deal (incorrect contact's attributes)"() {
        log.debug("Testing negative path (tests-to-fail)")
        when: "new contact is created"
        Contact sampleContact = createSampleContact(sampleCompanyName, isOrganization, ownerId)

        then: "new deal is not created"
        sleep(waitForWorkflowExecutionTimeout)
        await().atMost(waitForWorkflowExecutionTimeout, MILLISECONDS)
                    .pollInterval(awaitPollingInterval, MILLISECONDS)
                    .until {
            baseClient.contacts().get(sampleContact.id) && !baseClient.deals().list([contact_id: sampleContact.id])
        }


        where: "sample contact's attributes are"
        isOrganization  | ownerId
        false           | sampleSalesRepId
        true            | sampleAccountManagerId
        false           | sampleAccountManagerId
        true            | sampleOtherUserId
        false           | sampleOtherUserId

        // For the sake of @Unroll only
        dealName = null
        dealOwnerId = null
    }
}
