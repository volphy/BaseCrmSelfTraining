package com.solidbrain;

/**
 * Created by Krzysztof Wilk on 02/09/16.
 */

import com.getbase.Configuration
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit4.SpringRunner
import org.springframework.web.context.WebApplicationContext
import spock.lang.Specification;

import com.getbase.Client;

@ContextConfiguration  // makes Spock to start Spring context
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Slf4j
public class MilestoneOneTest extends Specification {

    @Autowired
    WebApplicationContext context

    Client baseClient;

    def setup() {
        baseClient = new Client(new Configuration.Builder()
                .accessToken(getAccessToken())
                .verbose()
                .build())
    }

    def getAccessToken() {
        return System.getenv("BASE_CRM_TOKEN")
    }

    def getAdminEmail() {
        return System.getenv("ADMIN_EMAIL")
    }

    def "admin user works"() {
        given:
            context != null
            baseClient instanceof Client

        when: "Fetching list of users"
            def response = baseClient.users()

        then: "Verifying if admin user is available"
            def adminEmail = getAdminEmail()
            response.self().email == adminEmail
            response.self().confirmed
            response.self().role == 'admin'
        }
}
