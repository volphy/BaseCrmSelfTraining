package com.solidbrain

import com.getbase.Client
import com.getbase.models.Contact
import com.getbase.Configuration
import com.getbase.models.Deal
import com.getbase.services.StagesService
import com.getbase.services.UsersService
import groovy.util.logging.Slf4j
import spock.lang.IgnoreIf
import spock.lang.Narrative
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Title
import spock.lang.Unroll

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

import static java.util.concurrent.TimeUnit.MILLISECONDS
import static java.util.concurrent.TimeUnit.SECONDS
import static org.awaitility.Awaitility.await


/**
 * Created by Krzysztof Wilk on 05/09/16.
 */

@IgnoreIf({ properties["integrationTest"] != "true" })
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
@Subject(Deal)
@Slf4j
class ContactAndNewDealSpec extends Specification {
    @Shared Client baseClient = new Client(new Configuration.Builder()
                                                        .accessToken(accessToken)
                                                        .build())

    @Shared def sampleSalesRepId = getSampleSalesRep()?.id
    @Shared def sampleAccountManagerId = getAccountManagerOnDuty()?.id
    @Shared def sampleOtherUserId = getSampleOtherUser()?.id

    static def dealNameDateFormat

    long waitForWorkflowExecutionTimeout = 30_000
    long awaitPollingInterval = 1_000
    long postDeleteTimeout = 60_000

    def getAccessToken() {
        def token = System.getProperty("BASE_CRM_TOKEN", System.getenv("BASE_CRM_TOKEN"))
        assert token
        return token
    }

    def getSampleSalesRep() {
        def emails = System.getProperty("workflow.sales.representatives.emails")
        assert emails

        def email = emails.split(",")[0].trim()

        def salesRep = baseClient.users().list(new UsersService.SearchCriteria().email(email))[0]
        assert salesRep
        return salesRep
    }

    def getAccountManagerOnDuty() {
        def email = System.getProperty("workflow.account.manager.on.duty.email").trim()
        assert email

        def accountManager = baseClient.users().list(new UsersService.SearchCriteria().email(email))[0]
        assert accountManager
        return accountManager
    }

    def getSampleOtherUser() {
        def emails = Arrays.asList(System.getProperty("workflow.sales.representatives.emails")
                                            .replaceAll(" ", "")
                                            .split(",")
                                    + System.getProperty("workflow.account.manager.on.duty.email")
                                            .replaceAll(" ", "")
                                            .split(","))

        def sampleUser = baseClient.users()
                                        .list(new UsersService.SearchCriteria()
                                        .confirmed(true)
                                        .status("active"))
                                    .stream()
                                    .filter {u -> !emails.contains(u.email)}
                                    .findFirst()
                                    .get()

        assert sampleUser
        return sampleUser
    }

    def setupSpec() {
        def file = new File("src/test/resources/test.properties")

        file.eachLine { l -> if (l.startsWith("workflow.deal.name.date.format")) {
                                    dealNameDateFormat = l.split("=")[1]
                                }
        }

        assert dealNameDateFormat
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
        "Some Company Of Mine"
    }

    def getSampleDealName(String contactName) {
        contactName + " " + ZonedDateTime.now().toLocalDate().format(DateTimeFormatter.ofPattern(dealNameDateFormat))
    }

    def "should create deal (correct contact's attributes)"() {
        log.debug("Testing positive path (test-to-pass)")
        when: "new contact that is a company owned by a sales rep is created"
        Contact sampleContact = baseClient.contacts().create([name : sampleCompanyName,
                                                              is_organization:  isOrganization,
                                                              owner_id: ownerId])

        then: "new deal at the first stage of the pipeline for this company is created"
        await().atMost(waitForWorkflowExecutionTimeout, MILLISECONDS).pollInterval(awaitPollingInterval, MILLISECONDS).until {
            !baseClient.deals().list([contact_id: sampleContact.id]).isEmpty()
        }
        Deal sampleDeal = baseClient.deals().list([contact_id: sampleContact.id])[0]
        sampleDeal.with {
            name == getSampleDealName(getSampleCompanyName())
            ownerId == dealOwnerId
            // dealStageId cannot be moved to where: because it is evaluated after test's completion
            stageId == getFirstStageId(sampleDeal)
        }

        where: "sample contact's attributes are"
        isOrganization << [true]
        ownerId << [sampleSalesRepId]
        dealName << [getSampleDealName(sampleCompanyName)]
        dealOwnerId << [sampleSalesRepId]
    }

    def getFirstStageId(Deal deal) {
        if (Objects.isNull(deal)) {
            throw new IllegalArgumentException("Deal cannot be null")
        }

        def stage = baseClient.stages()
                .list(new StagesService.SearchCriteria()
                .active(true))
                .stream()
                .filter { s -> s.position == 1 && deal.stageId == s.id }
                .findFirst()

        if (stage.isPresent()) {
            stage.get().id
        } else {
            throw new IllegalStateException("Deal is incorrect. Deal={}", deal)
        }
    }

    def "should not create deal (incorrect contact's attributes)"() {
        log.debug("Testing negative path (tests-to-fail)")
        when: "new contact is created"
        Contact sampleContact = baseClient.contacts().create([name : sampleCompanyName,
                                                                is_organization: isOrganization,
                                                                owner_id: ownerId])

        then: "new deal is not created"
        sleep(waitForWorkflowExecutionTimeout)
        !baseClient.deals().list([contact_id: sampleContact.id])

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
