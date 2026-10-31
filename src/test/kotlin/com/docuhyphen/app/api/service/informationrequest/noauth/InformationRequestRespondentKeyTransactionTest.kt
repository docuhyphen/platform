package com.docuhyphen.app.api.service.informationrequest.noauth

import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.migration.queryString
import com.docuhyphen.app.api.model.dto.InformationRequestResponseWorkspaceDto
import com.docuhyphen.app.api.model.entity.RequestAccessSessionVerificationStrength
import com.docuhyphen.app.api.model.entity.ShareLink
import com.docuhyphen.app.api.model.entity.ShareLinkMode
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.resource.informationrequest.InformationRequestNoAuthRequestResource
import com.docuhyphen.app.api.resource.model.InformationRequestResponsePatchRequest
import com.docuhyphen.app.api.resource.model.PatchInformationRequestResponsesRequest
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.command.CommandRequestFingerprint
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.exchange.ShareLinkService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.noauth.model.ReassignedRespondentLink
import com.docuhyphen.app.api.service.informationrequest.occurrence.InformationRequestGroupOccurrenceService
import com.docuhyphen.app.api.service.informationrequest.party.InformationRequestPartyQueryService
import com.docuhyphen.app.api.service.informationrequest.response.InformationRequestResponseDraftService
import com.docuhyphen.app.api.service.informationrequest.response.InformationRequestResponseWorkspaceService
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.sql.Timestamp
import java.time.Instant
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestRespondentKeyTransactionTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var readAccess: InformationRequestNoAuthReadAccessService

    @Inject
    lateinit var links: ShareLinkService

    @Inject
    lateinit var sessions: RequestAccessSessionService

    @Inject
    lateinit var parties: InformationRequestPartyQueryService

    @Inject
    lateinit var responses: InformationRequestResponseDraftService

    @Inject
    lateinit var occurrences: InformationRequestGroupOccurrenceService

    @Inject
    lateinit var workspaces: InformationRequestResponseWorkspaceService

    @Inject
    lateinit var requestRepository: InformationRequestRepository

    @Test
    fun `a renewed session and a replacement link for the same recipient keep the respondent key`() {
        val scenario = NoAuthSessionSqlScenario(dataSource, links, sessions)
        val first = scenario.issueSession()
        val firstKey = respondentKey(scenario.rawToken, scenario.requestId, first.sessionToken)

        dataSource.revokeAccessSession(first.session.id)
        val renewed = scenario.issueSession()
        val renewedKey = respondentKey(scenario.rawToken, scenario.requestId, renewed.sessionToken)

        val replacementToken = "respondent-key-${UUID.randomUUID()}"
        val replacement = replaceLink(scenario.link, replacementToken)
        val replacementSession = sessions.issue(replacement, scenario.participant, EMAIL_OTP, null)
        val replacementKey = respondentKey(replacementToken, scenario.requestId, replacementSession.sessionToken)

        assertTrue(firstKey.isNotBlank())
        assertEquals(firstKey, renewedKey)
        assertEquals(firstKey, replacementKey)
    }

    @Test
    fun `a recipient the party is reassigned to gets their own respondent key`() {
        val scenario = NoAuthSessionSqlScenario(dataSource, links, sessions)
        val former = scenario.issueSession()
        val formerKey = respondentKey(scenario.rawToken, scenario.requestId, former.sessionToken)

        val reassigned = reassign(scenario)

        assertTrue(reassigned.key.isNotBlank())
        assertNotEquals(formerKey, reassigned.key)
    }

    @Test
    fun `answers kept for a reassigned party's former recipient are refused and nothing of them is saved`() {
        val scenario = NoAuthSessionSqlScenario(dataSource, links, sessions)
        val former = scenario.issueSession()
        val formerKey = respondentKey(scenario.rawToken, scenario.requestId, former.sessionToken)
        val reassigned = reassign(scenario)

        val refused = save(reassigned.rawToken, scenario, reassigned.sessionToken, KEPT_NARRATIVE, formerKey)
        val own = save(reassigned.rawToken, scenario, reassigned.sessionToken, OWN_NARRATIVE, reassigned.key)

        assertEquals(Response.Status.CONFLICT.statusCode, refused.status, refused.entity.toString())
        assertEquals(InformationRequestErrorCatalog.RESPONDENT_CHANGED, (refused.entity as ResponseError).reasonCode)
        assertEquals(0, scenario.responsesWithNarrative(KEPT_NARRATIVE))
        assertEquals(Response.Status.OK.statusCode, own.status, own.entity.toString())
        assertEquals(OWN_NARRATIVE, scenario.currentDocumentNarrative())
    }

    @Test
    fun `a save naming its own respondent replays through a renewed session without saving again`() {
        val scenario = NoAuthSessionSqlScenario(dataSource, links, sessions)
        val first = scenario.issueSession()
        val key = respondentKey(scenario.rawToken, scenario.requestId, first.sessionToken)
        val commandKey = "respondent-key-${UUID.randomUUID()}"

        val recorded = save(scenario.rawToken, scenario, first.sessionToken, KEPT_NARRATIVE, key, commandKey)
        val later = save(scenario.rawToken, scenario, first.sessionToken, OWN_NARRATIVE, key)
        dataSource.revokeAccessSession(first.session.id)
        val renewed = scenario.issueSession()
        val replayed = save(scenario.rawToken, scenario, renewed.sessionToken, KEPT_NARRATIVE, key, commandKey)

        assertEquals(Response.Status.OK.statusCode, recorded.status, recorded.entity.toString())
        assertEquals(Response.Status.OK.statusCode, later.status, later.entity.toString())
        assertEquals(Response.Status.OK.statusCode, replayed.status, replayed.entity.toString())
        assertEquals(OWN_NARRATIVE, scenario.currentDocumentNarrative())
    }

    private fun respondentKey(rawToken: String, requestId: UUID, sessionToken: String): String {
        val response = adapter().responseWorkspace(requestId.toString(), rawToken, sessionToken)
        assertEquals(Response.Status.OK.statusCode, response.status, response.entity.toString())
        return (response.entity as InformationRequestResponseWorkspaceDto).respondentKey
    }

    private fun responseETag(requestId: UUID): String =
        QuarkusTransaction.requiringNew().call {
            InformationRequestETag.responsesOf(requireNotNull(requestRepository.findById(requestId)))
        }

    private fun save(
        rawToken: String,
        scenario: NoAuthSessionSqlScenario,
        sessionToken: String,
        narrative: String,
        respondentKey: String,
        commandKey: String = "respondent-key-${UUID.randomUUID()}",
    ): Response =
        adapter().patchResponses(
            scenario.requestId.toString(),
            PatchInformationRequestResponsesRequest(
                listOf(
                    InformationRequestResponsePatchRequest(
                        scenario.runtime.documentRequirementId,
                        narrative = narrative
                    )
                ),
                respondentKey = respondentKey,
            ),
            rawToken,
            responseETag(scenario.requestId),
            commandKey,
            sessionToken,
        )

    private fun adapter() =
        InformationRequestNoAuthRequestResource(readAccess, parties, responses, occurrences, workspaces)

    private fun replaceLink(replaced: ShareLink, rawToken: String): ShareLink = QuarkusTransaction.requiringNew().call {
        sessions.revokeAllForShareLink(replaced.id)
        dataSource.connection.use { connection ->
            execute(connection, "UPDATE share_link SET status = 'REVOKED' WHERE id = ?", replaced.id)
        }
        bootstrapLink(replaced.shareId, rawToken, replaced.id)
    }

    private fun reassign(scenario: NoAuthSessionSqlScenario): ReassignedRespondentLink {
        QuarkusTransaction.requiringNew().run { sessions.revokeAllForShareLink(scenario.link.id) }
        val recipient =
            dataSource.connection.use { connection -> contributorAsParticipant(connection, scenario.runtime) }
        val shareId = dataSource.connection.use { connection ->
            UUID.fromString(
                queryString(
                    connection,
                    "SELECT share_id::text FROM information_request_party WHERE id = ?",
                    scenario.runtime.contributorPartyId,
                ),
            )
        }
        val rawToken = "respondent-key-${UUID.randomUUID()}"
        val link = QuarkusTransaction.requiringNew().call { bootstrapLink(shareId, rawToken, null) }
        val session = sessions.issue(link, PrincipalRef.participant(recipient), EMAIL_OTP, null)
        return ReassignedRespondentLink(
            rawToken,
            session.sessionToken,
            respondentKey(rawToken, scenario.requestId, session.sessionToken)
        )
    }

    private fun bootstrapLink(shareId: UUID, rawToken: String, replaces: UUID?): ShareLink =
        links.create(
            ShareLink().apply {
                this.shareId = shareId
                tokenHash = CommandRequestFingerprint.sha256Hex(rawToken)
                linkMode = ShareLinkMode.VERIFICATION_BOOTSTRAP
                verificationStrength = EMAIL_OTP
                replacesShareLinkId = replaces
                expiresAt = Timestamp.from(Instant.now().plusSeconds(3600))
            },
        )

    private companion object {
        val EMAIL_OTP = RequestAccessSessionVerificationStrength.EMAIL_OTP
        const val KEPT_NARRATIVE = "An answer kept in the browser for the earlier recipient"
        const val OWN_NARRATIVE = "An answer entered by the current recipient"
    }
}
