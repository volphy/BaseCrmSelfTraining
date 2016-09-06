package com.solidbrain;

/**
 * Created by Krzysztof Wilk on 02/09/16.
 */

import com.getbase.Configuration
import com.getbase.models.User
import com.getbase.services.UsersService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit4.SpringRunner
import org.springframework.web.context.WebApplicationContext
import spock.lang.Specification
import spock.lang.Shared

import com.getbase.Client

@ContextConfiguration  // makes Spock start Spring context
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class UsersTest extends Specification {

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

    def getAdminEmail() {
        return System.getenv("ADMIN_EMAIL")
    }

    def "admin user exists"() {
        given:
            adminEmail

        when: "Fetching list of users"
            def response = baseClient.users()

        then: "Verifying if admin user exists"
            response.self().email == adminEmail
            response.self().confirmed
            response.self().role == "admin"
            response.self().status == "active"
    }

    def getAccountManagerEmail() {
        return "chrismwilk+accountmanager+sara@gmail.com"
    }

    def "account manager exists"() {
        given:
        accountManagerEmail

        when: "Fetching list of users"
        def response = baseClient.users()
        def amEmailsPattern = "\\+accountmanager\\+"
        def amUsers = response.list(new UsersService.SearchCriteria().
                                                        asMap()).
                                findAll { it.role == "user" &&
                                            it.status == "active" &&
                                            it.confirmed &&
                                            it.email =~ amEmailsPattern}

        then: "Verifying if account manager exists"
        amUsers.size() >= 1
        amUsers.get(0).email == accountManagerEmail
        amUsers.get(0).confirmed
        amUsers.get(0).status == "active"
    }

    def "sales representatives exist"() {
        when: "Fetching list of users"
        def response = baseClient.users()
        def salesRepEmailsPattern = "\\+salesrep\\+"
        def salesRepUsers = response.list(new UsersService.SearchCriteria().
                                                                asMap()).
                                    findAll { it.role == "user" &&
                                                it.status == "active" &&
                                                it.confirmed &&
                                                it.email =~ salesRepEmailsPattern}

        then: "Verifying if sales representatives exist"
        salesRepUsers.size() >= 3
        salesRepUsers.forEach { it.confirmed}
        salesRepUsers.forEach { it.status == "active"}
    }
}
