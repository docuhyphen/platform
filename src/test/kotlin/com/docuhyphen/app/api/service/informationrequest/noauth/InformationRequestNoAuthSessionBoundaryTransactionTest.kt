package com.docuhyphen.app.api.service.informationrequest.noauth

import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.migration.queryInt
import com.docuhyphen.app.api.model.informationrequest.InformationRequestAuditActor
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.resource.informationrequest.InformationRequestNoAuthRequestResource
import com.docuhyphen.app.api.resource.model.InformationRequestResponsePatchRequest
import com.docuhyphen.app.api.resource.model.PatchInformationRequestResponsesRequest
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.model.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.exchange.ShareLinkService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.occurrence.InformationRequestGroupOccurrenceService
import com.docuhyphen.app.api.service.informationrequest.parent.InformationRequestParentLifecycleService
import com.docuhyphen.app.api.service.informationrequest.party.InformationRequestPartyQueryService
import com.docuhyphen.app.api.service.informationrequest.response.InformationRequestResponseDraftService
import com.docuhyphen.app.api.service.informationrequest.response.InformationRequestResponseWorkspaceService
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestNoAuthSessionBoundaryTransactionTest {
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
    lateinit var parentLifecycle: InformationRequestParentLifecycleService

    @Inject
    lateinit var requestRepository: InformationRequestRepository

    @ParameterizedTest(name = "{0}")
    @ValueSource(
        strings = [
            NO_CUTOFF,
            REVOKED_BEFORE_RESOLUTION,
            REVOKED_AFTER_RESOLUTION,
            SIGN_IN_REQUIRED_AFTER_RESOLUTION,
            LINK_EXPIRED_AFTER_RESOLUTION,
        ],
    )
    fun `a response sent through an access link is saved only while its session is still current`(cutoff: String) {
        val scenario = NoAuthSessionSqlScenario(dataSource, links, sessions)
        val issued = scenario.issueSession()
        val currentETag = responseETag(scenario)
        if (cutoff == REVOKED_BEFORE_RESOLUTION) dataSource.revokeAccessSession(issued.session.id)

        val response = adapter(scenario, issued.sessionToken) { cut(cutoff, scenario, issued.session.id) }
            .patchResponses(
                scenario.requestId.toString(),
                patch(scenario),
                scenario.rawToken,
                currentETag,
                "session-boundary-${UUID.randomUUID()}",
                issued.sessionToken,
            )

        val expected = REFUSALS[cutoff]
        if (expected == null) {
            assertEquals(Response.Status.OK.statusCode, response.status, response.entity.toString())
            assertEquals(1, scenario.responsesWithNarrative(NARRATIVE))
        } else {
            assertEquals(Response.Status.CONFLICT.statusCode, response.status, response.entity.toString())
            assertEquals(expected, (response.entity as ResponseError).reasonCode)
            assertEquals(0, scenario.responsesWithNarrative(NARRATIVE))
        }
    }

    @Test
    fun `a save recorded before its session ended replays through a newly verified session without saving again`() {
        val scenario = NoAuthSessionSqlScenario(dataSource, links, sessions)
        val first = scenario.issueSession()
        val recordedKey = "session-boundary-${UUID.randomUUID()}"
        val recorded = save(scenario, first.sessionToken, recordedKey, NARRATIVE)
        val later = save(scenario, first.sessionToken, "session-boundary-${UUID.randomUUID()}", LATER_NARRATIVE)
        val latestETag = responseETag(scenario)

        dataSource.revokeAccessSession(first.session.id)
        val refused = save(scenario, first.sessionToken, recordedKey, NARRATIVE, latestETag)
        val second = scenario.issueSession()
        val replayed = save(scenario, second.sessionToken, recordedKey, NARRATIVE)

        assertEquals(Response.Status.OK.statusCode, recorded.status, recorded.entity.toString())
        assertEquals(Response.Status.OK.statusCode, later.status, later.entity.toString())
        assertEquals(Response.Status.CONFLICT.statusCode, refused.status, refused.entity.toString())
        assertEquals(
            InformationRequestErrorCatalog.ACCESS_SESSION_REVOKED,
            (refused.entity as ResponseError).reasonCode
        )
        assertEquals(Response.Status.OK.statusCode, replayed.status, replayed.entity.toString())
        assertEquals(LATER_NARRATIVE, scenario.currentDocumentNarrative())
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = [NO_CUTOFF, REVOKED_AFTER_RESOLUTION])
    fun `a workspace read through an access link answers only while its session is still current`(cutoff: String) {
        val scenario = NoAuthSessionSqlScenario(dataSource, links, sessions)
        val issued = scenario.issueSession()

        val response = adapter(scenario, issued.sessionToken) { cut(cutoff, scenario, issued.session.id) }
            .responseWorkspace(scenario.requestId.toString(), scenario.rawToken, issued.sessionToken)

        val firstViews = dataSource.connection.use { connection ->
            queryInt(
                connection,
                "SELECT count(*) FROM information_request WHERE id = ? AND first_viewed_at IS NOT NULL",
                scenario.requestId,
            )
        }
        if (cutoff == NO_CUTOFF) {
            assertEquals(Response.Status.OK.statusCode, response.status, response.entity.toString())
            assertEquals(1, firstViews)
        } else {
            assertEquals(Response.Status.CONFLICT.statusCode, response.status, response.entity.toString())
            assertEquals(
                InformationRequestErrorCatalog.ACCESS_SESSION_REVOKED,
                (response.entity as ResponseError).reasonCode
            )
            assertEquals(0, firstViews)
        }
    }

    private fun save(
        scenario: NoAuthSessionSqlScenario,
        sessionToken: String,
        key: String,
        narrative: String,
        ifMatch: String = responseETag(scenario),
    ): Response =
        adapter(scenario, sessionToken) {}.patchResponses(
            scenario.requestId.toString(),
            patch(scenario, narrative),
            scenario.rawToken,
            ifMatch,
            key,
            sessionToken,
        )

    private fun responseETag(scenario: NoAuthSessionSqlScenario): String =
        QuarkusTransaction.requiringNew().call {
            InformationRequestETag.responsesOf(requireNotNull(requestRepository.findById(scenario.requestId)))
        }

    private fun patch(scenario: NoAuthSessionSqlScenario, narrative: String = NARRATIVE) =
        PatchInformationRequestResponsesRequest(
            listOf(
                InformationRequestResponsePatchRequest(
                    scenario.runtime.documentRequirementId,
                    narrative = narrative
                )
            ),
        )

    private fun adapter(
        scenario: NoAuthSessionSqlScenario,
        sessionToken: String,
        afterResolution: () -> Unit,
    ): InformationRequestNoAuthRequestResource {
        val boundary = mock<InformationRequestNoAuthReadAccessService>()
        whenever(boundary.resolve(scenario.requestId, scenario.rawToken, sessionToken)).thenAnswer {
            readAccess.resolve(scenario.requestId, scenario.rawToken, sessionToken).also { afterResolution() }
        }
        doAnswer {
            readAccess.readCurrent(
                it.getArgument(0),
                it.getArgument(1),
                it.getArgument<(RequestAccessContext) -> Any?>(2)
            )
        }
            .whenever(boundary).readCurrent<Any?>(any(), any(), any())
        return InformationRequestNoAuthRequestResource(boundary, parties, responses, occurrences, workspaces)
    }

    private fun cut(cutoff: String, scenario: NoAuthSessionSqlScenario, sessionId: UUID) {
        when (cutoff) {
            REVOKED_AFTER_RESOLUTION -> dataSource.revokeAccessSession(sessionId)
            LINK_EXPIRED_AFTER_RESOLUTION -> scenario.expireLink()
            SIGN_IN_REQUIRED_AFTER_RESOLUTION -> QuarkusTransaction.requiringNew().run {
                dataSource.connection.use { connection ->
                    execute(
                        connection,
                        "UPDATE exchange SET require_recipient_sign_in = TRUE WHERE id = ?",
                        scenario.runtime.exchangeId,
                    )
                }
                parentLifecycle.enforceRecipientSignIn(
                    scenario.runtime.exchangeId,
                    InformationRequestAuditActor.of(
                        RequestAccessContext(
                            PrincipalRef.user(scenario.runtime.template.userId),
                            AuthorizationContext()
                        ),
                    ),
                )
            }
        }
    }

    private companion object {
        const val NARRATIVE = "A response sent through an access link"
        const val LATER_NARRATIVE = "A later response sent through the same access link"
        const val NO_CUTOFF = "no cutoff"
        const val REVOKED_BEFORE_RESOLUTION = "session revoked before it is resolved"
        const val REVOKED_AFTER_RESOLUTION = "session revoked after it is resolved"
        const val SIGN_IN_REQUIRED_AFTER_RESOLUTION = "recipient sign-in required after it is resolved"
        const val LINK_EXPIRED_AFTER_RESOLUTION = "access link expired after it is resolved"

        val REFUSALS = mapOf(
            REVOKED_BEFORE_RESOLUTION to InformationRequestErrorCatalog.ACCESS_SESSION_REVOKED,
            REVOKED_AFTER_RESOLUTION to InformationRequestErrorCatalog.ACCESS_SESSION_REVOKED,
            SIGN_IN_REQUIRED_AFTER_RESOLUTION to InformationRequestErrorCatalog.RECIPIENT_SIGN_IN_REQUIRED,
            LINK_EXPIRED_AFTER_RESOLUTION to InformationRequestErrorCatalog.ACCESS_LINK_EXPIRED,
        )
    }
}
