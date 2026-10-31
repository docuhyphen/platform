package com.docuhyphen.app.api.resource.informationrequest.lifecycle

import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp
import com.docuhyphen.app.api.resource.informationrequest.lifecycle.model.CarryForwardAccessLink
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.command.CommandRequestFingerprint
import com.docuhyphen.app.api.service.communication.OtpService
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.InformationRequestRuntimeTestServices
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestCarryForwardDisclosureFixture
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestCarryForwardDisclosureFixture.Companion.EARLIER_NARRATIVE
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestCarryForwardDisclosureFixture.Companion.EARLIER_VALUE
import com.docuhyphen.app.api.service.informationrequest.lifecycle.model.CarryForwardDisclosureScenario
import com.docuhyphen.app.api.service.informationrequest.noauth.InformationRequestContactProofService
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import io.restassured.response.Response
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestNoAuthCarryForwardDisclosureHttpTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var runtime: InformationRequestRuntimeTestServices

    @Inject
    lateinit var requestRepository: InformationRequestRepository

    @Inject
    lateinit var authorizationService: AuthorizationService

    @Inject
    lateinit var contactProof: InformationRequestContactProofService

    @Inject
    lateinit var otp: OtpService

    private val fixture by lazy {
        InformationRequestCarryForwardDisclosureFixture(dataSource, runtime, requestRepository, authorizationService)
    }

    @Test
    fun `an access-link respondent is offered a protected earlier answer only while its session is recently verified`() {
        val scenario = fixture.scenario(protectedSource = true)
        val link = openLink(scenario)

        val verified = carryForwards(scenario, link)
        ageSession(link)
        val expired = carryForwards(scenario, link)

        assertEquals(200, verified.statusCode, verified.body.asString())
        assertTrue(verified.body.asString().contains(EARLIER_NARRATIVE), verified.body.asString())
        assertEquals(200, expired.statusCode, expired.body.asString())
        assertNoEarlierAnswer(expired)
    }

    @Test
    fun `an access-link respondent whose source party was withdrawn is not shown the earlier answer`() {
        val scenario = fixture.scenario()
        val link = openLink(scenario)
        val before = carryForwards(scenario, link)

        fixture.withdrawSourceAccess(scenario, scenario.participant)
        val after = carryForwards(scenario, link)

        assertTrue(before.body.asString().contains(EARLIER_NARRATIVE), before.body.asString())
        assertEquals(200, after.statusCode, after.body.asString())
        assertNoEarlierAnswer(after)
    }

    private fun carryForwards(scenario: CarryForwardDisclosureScenario, link: CarryForwardAccessLink): Response =
        given()
            .header(InformationRequestCommandHttp.ACCESS_LINK_TOKEN_HEADER, link.token)
            .header(InformationRequestCommandHttp.SESSION_TOKEN_HEADER, link.sessionToken)
            .get("/no-auth/information-requests/${scenario.successorId}/carry-forwards")

    private fun assertNoEarlierAnswer(response: Response) {
        val body = response.body.asString()
        assertFalse(body.contains(EARLIER_NARRATIVE), body)
        assertFalse(body.contains(EARLIER_VALUE), body)
        assertFalse(body.contains("OFFERED"), body)
    }

    private fun openLink(scenario: CarryForwardDisclosureScenario): CarryForwardAccessLink {
        val token = "carry-forward-link-${UUID.randomUUID()}"
        val linkId = UUID.randomUUID()
        dataSource.connection.use { connection ->
            execute(
                connection,
                """
                INSERT INTO share_link
                    (id, share_id, token_hash, status, link_mode, used_count, require_mfa, contact_otp_failed_attempts,
                     contact_otp_challenge_count, rotation_count, max_uses, expires_at, created_at, verification_strength,
                     contact_otp_hash, contact_otp_expires_at)
                VALUES (?, ?, ?, 'ACTIVE', 'VERIFICATION_BOOTSTRAP', 0, FALSE, 0, 1, 0, 5, now() + interval '30 days', now(),
                        'EMAIL_OTP', ?, now() + interval '10 minutes')
                """.trimIndent(),
                linkId,
                fixture.successorPartyShareId(scenario, scenario.participant),
                CommandRequestFingerprint.sha256Hex(token),
                otp.hashOtp(CONTACT_CODE),
            )
        }
        return CarryForwardAccessLink(linkId, token, contactProof.verifyChallenge(token, CONTACT_CODE).sessionToken)
    }

    private fun ageSession(link: CarryForwardAccessLink) {
        dataSource.connection.use { connection ->
            execute(
                connection,
                "UPDATE request_access_session SET issued_at = now() - interval '1 hour', stepped_up_at = NULL WHERE share_link_id = ?",
                link.id,
            )
        }
    }

    private companion object {
        const val CONTACT_CODE = "246810"
    }
}
