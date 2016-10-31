package com.solidbrain.workflow

import com.getbase.Client
import com.getbase.Configuration
import com.getbase.models.Contact
import com.getbase.models.Deal
import com.getbase.services.ContactsService
import com.getbase.services.StagesService
import com.getbase.services.UsersService
import spock.lang.Shared
import spock.lang.Specification

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

import static java.util.concurrent.TimeUnit.MILLISECONDS
import static java.util.concurrent.TimeUnit.SECONDS
import static java.util.stream.Collectors.toList
import static org.awaitility.Awaitility.await

/**
 * Created by Krzysztof Wilk on 31/10/2016.
 */
abstract class AbstractSpec extends Specification {

    @Shared Client baseClient = new Client(new Configuration.Builder()
            .accessToken(accessToken)
            .build())

    @Shared def sampleSalesRepId = getSampleSalesRep()?.id
    @Shared def sampleAccountManagerId = getAccountManagerOnDuty()?.id
    @Shared def sampleOtherUserId = getSampleOtherUser()?.id

    @Shared List<Long> allAccountManagersIds = getAllAccountManagers()*.id

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
        def emails = System.getProperty("workflow.sales.representatives.emails", System.getenv("workflow_sales_representatives_emails"))
        assert emails

        def email = emails.split(",")[0].trim()

        def salesRep = baseClient.users().list(new UsersService.SearchCriteria().email(email))[0]
        assert salesRep
        return salesRep
    }

    def getAccountManagerOnDuty() {
        def email = System.getProperty("workflow.account.manager.on.duty.email", System.getenv("workflow_account_manager_on_duty_email")).trim()
        assert email

        def accountManager = baseClient.users().list(new UsersService.SearchCriteria().email(email))[0]
        assert accountManager
        return accountManager
    }

    def getAllAccountManagers() {
        def emails = Arrays.asList(System.getProperty("workflow.account.managers.emails",
                System.getenv("workflow_account_managers_emails"))
                .replaceAll(" ", "")
                .split(","))
        assert emails

        def accountManagers = baseClient.users().list(new UsersService.SearchCriteria()
                .confirmed(true)
                .status("active"))
                .stream()
                .filter { u -> emails.contains(u.getEmail()) }
                .collect(toList())
        assert accountManagers
        return accountManagers
    }

    def getSampleOtherUser() {
        def emails = Arrays.asList(System.getProperty("workflow.sales.representatives.emails",
                System.getenv("workflow_sales_representatives_emails"))
                .replaceAll(" ", "")
                .split(",")
                + System.getProperty("workflow.account.managers.emails",
                System.getenv("workflow_account_managers_emails"))
                .replaceAll(" ", "")
                .split(","))
        assert emails

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

    def Contact createSampleContact(String name, boolean isOrganization, long ownerId) {
        assert !baseClient.contacts().list(new ContactsService.SearchCriteria().name(name))

        def contact = baseClient.contacts().create([name           : name,
                                                    is_organization: isOrganization,
                                                    owner_id       : ownerId])

        assert contact
        return contact
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

    def getWonStageId(Deal deal) {
        if (Objects.isNull(deal)) {
            throw new IllegalArgumentException("Deal cannot be null")
        }

        def stage = baseClient.stages()
                .list(new StagesService.SearchCriteria().name("won"))
                .stream()
                .findFirst()

        if (stage.isPresent()) {
            stage.get().id
        } else {
            throw new IllegalStateException("Deal is incorrect. Deal={}", deal)
        }
    }
}
