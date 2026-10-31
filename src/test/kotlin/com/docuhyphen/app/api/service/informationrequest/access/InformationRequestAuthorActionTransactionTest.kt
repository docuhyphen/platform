package com.docuhyphen.app.api.service.informationrequest.access

import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.model.entity.InformationRequestBusinessDecisionKind
import com.docuhyphen.app.api.model.informationrequest.acceptedfact.RecordInformationRequestBusinessDecisionCommand
import com.docuhyphen.app.api.model.informationrequest.access.InformationRequestAuthorActions
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.resource.informationrequest.InformationRequestSignedInCaller
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.InformationRequestRuntimeTestServices
import com.docuhyphen.app.api.service.informationrequest.acceptedfact.InformationRequestBusinessDecisionService
import com.docuhyphen.app.api.service.informationrequest.access.model.AuthorActionPlacement
import com.docuhyphen.app.api.service.informationrequest.conformance.ConformanceRequestSupport
import com.docuhyphen.app.api.service.informationrequest.conformance.model.ConformanceRequest
import com.docuhyphen.app.api.service.user.AppUserService
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.security.ForbiddenException
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.time.Instant
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestAuthorActionTransactionTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var runtime: InformationRequestRuntimeTestServices

    @Inject
    lateinit var requests: InformationRequestRepository

    @Inject
    lateinit var authorActions: InformationRequestAuthorActionService

    @Inject
    lateinit var decisions: InformationRequestBusinessDecisionService

    @Inject
    lateinit var appUsers: AppUserService

    private val support by lazy { ConformanceRequestSupport(dataSource, runtime, requests) }

    @ParameterizedTest(name = "{0}")
    @MethodSource("placements")
    fun `the owner is offered exactly the author actions its parent Exchange and request state permit`(name: String) {
        val placement = PLACEMENTS.getValue(name)
        val request = ownedRequest()
        place(request, placement)

        val offered = QuarkusTransaction.requiringNew().call {
            authorActions.actionsFor(requireNotNull(requests.findById(request.requestId)), support.owner(request))
        }

        assertEquals(placement.expected, offered)
    }

    @Test
    fun `access links are offered only while the Exchange lets participants answer without signing in`() {
        val request = ownedRequest()
        signInRequired(request, false)

        val withoutSignIn = offeredTo(request)
        assertTrue(withoutSignIn.manageParties)
        assertTrue(withoutSignIn.issueAccessLinks)

        signInRequired(request, true)

        val withSignIn = offeredTo(request)
        assertTrue(withSignIn.manageParties)
        assertFalse(withSignIn.issueAccessLinks)
    }

    @Test
    fun `a respondent is offered no author action`() {
        val request = ownedRequest()

        val offered = QuarkusTransaction.requiringNew().call {
            authorActions.actionsFor(requireNotNull(requests.findById(request.requestId)), support.contributor(request))
        }

        assertEquals(InformationRequestAuthorActions(), offered)
    }

    @Test
    fun `the workspace carries the author actions to the owner and none to a respondent`() {
        val request = ownedRequest()
        place(request, PLACEMENTS.getValue(ENDED_CLOSED))
        val owner = InformationRequestSignedInCaller.signIn(appUsers, request.runtime.template.userId)
        val contributor = InformationRequestSignedInCaller.signIn(appUsers, request.runtime.contributorUserId)

        val owned = owner.authorized().get("/information-requests/${request.requestId}/response-workspace")
        val answered = contributor.authorized().get("/information-requests/${request.requestId}/response-workspace")

        assertEquals(200, owned.statusCode, owned.asString())
        assertEquals(200, answered.statusCode, answered.asString())
        assertEquals(flags(retained), owned.jsonPath().getMap<String, Boolean>("authorActions"))
        assertEquals(
            flags(InformationRequestAuthorActions()),
            answered.jsonPath().getMap<String, Boolean>("authorActions")
        )
    }

    @Test
    fun `recording a decision succeeds or is refused exactly as the closed request's projection says`() {
        val active = ownedRequest()
        place(active, PLACEMENTS.getValue(ACTIVE_CLOSED))
        val ended = ownedRequest()
        place(ended, PLACEMENTS.getValue(ENDED_CLOSED))

        val recorded = QuarkusTransaction.requiringNew().call { decisions.record(decision(active)) }
        assertThrows(ForbiddenException::class.java) {
            QuarkusTransaction.requiringNew().call { decisions.record(decision(ended)) }
        }

        assertEquals(active.requestId, recorded.decision.informationRequestId)
        assertEquals(true, PLACEMENTS.getValue(ACTIVE_CLOSED).expected.recordDecisions)
        assertEquals(false, PLACEMENTS.getValue(ENDED_CLOSED).expected.recordDecisions)
    }

    private fun offeredTo(request: ConformanceRequest): InformationRequestAuthorActions =
        QuarkusTransaction.requiringNew().call {
            authorActions.actionsFor(requireNotNull(requests.findById(request.requestId)), support.owner(request))
        }

    private fun signInRequired(request: ConformanceRequest, required: Boolean) {
        dataSource.connection.use { connection ->
            execute(
                connection,
                "UPDATE exchange SET require_recipient_sign_in = ? WHERE id = ?",
                required,
                request.runtime.exchangeId,
            )
        }
    }

    private fun decision(request: ConformanceRequest) =
        RecordInformationRequestBusinessDecisionCommand(
            requestId = request.requestId,
            owningProcessKey = "record-review",
            outcomeCode = "completed",
            kind = InformationRequestBusinessDecisionKind.ORIGINAL,
            decidedAt = Instant.now().minusSeconds(60),
            access = support.owner(request),
            idempotencyKey = "decision-${UUID.randomUUID()}",
        )

    private fun ownedRequest(): ConformanceRequest =
        support.fieldRequest(prepare = { connection, request ->
            execute(
                connection,
                """
                INSERT INTO share (id, resource_type, resource_id, principal_kind, principal_id, role_name, source, status, granted_at)
                VALUES (?, 'EXCHANGE', ?, 'USER', ?, 'OWNER', 'DIRECT', 'ACTIVE', now())
                """.trimIndent(),
                UUID.randomUUID(),
                request.runtime.exchangeId,
                request.runtime.template.userId,
            )
        })

    private fun place(request: ConformanceRequest, placement: AuthorActionPlacement) {
        dataSource.connection.use { connection ->
            if (placement.requestState == "CLOSED") {
                execute(
                    connection,
                    "UPDATE information_request SET state = 'CLOSED', closed_at = now() WHERE id = ?",
                    request.requestId
                )
            }
            execute(
                connection,
                "UPDATE exchange SET status = ?, is_deleted = ? WHERE id = ?",
                placement.parentStatus,
                placement.parentDeleted,
                request.runtime.exchangeId,
            )
        }
    }

    private fun flags(actions: InformationRequestAuthorActions): Map<String, Boolean> =
        mapOf(
            "issue" to actions.issue,
            "cancel" to actions.cancel,
            "supersede" to actions.supersede,
            "changeCompletionGate" to actions.changeCompletionGate,
            "manageParties" to actions.manageParties,
            "previewRecipients" to actions.previewRecipients,
            "manageClocks" to actions.manageClocks,
            "scheduleFollowUps" to actions.scheduleFollowUps,
            "createFollowUps" to actions.createFollowUps,
            "promoteFacts" to actions.promoteFacts,
            "revokeFacts" to actions.revokeFacts,
            "recordDecisions" to actions.recordDecisions,
            "issueAccessLinks" to actions.issueAccessLinks,
        )

    companion object {
        private const val ACTIVE_CLOSED = "active Exchange, closed request"
        private const val ENDED_CLOSED = "ended Exchange, closed request"

        private val open = InformationRequestAuthorActions(
            cancel = true,
            supersede = true,
            changeCompletionGate = true,
            manageParties = true,
            issueAccessLinks = true,
            previewRecipients = true,
            manageClocks = true,
            scheduleFollowUps = true,
            createFollowUps = true,
            promoteFacts = true,
            revokeFacts = true,
            recordDecisions = true,
        )

        private val closed = InformationRequestAuthorActions(
            scheduleFollowUps = true,
            createFollowUps = true,
            promoteFacts = true,
            revokeFacts = true,
            recordDecisions = true,
        )

        private val retained = InformationRequestAuthorActions(revokeFacts = true)

        private val PLACEMENTS: Map<String, AuthorActionPlacement> = linkedMapOf(
            "active Exchange, open request" to AuthorActionPlacement("ACCEPTED_STARTED", false, "ISSUED", open),
            ACTIVE_CLOSED to AuthorActionPlacement("ACCEPTED_STARTED", false, "CLOSED", closed),
            ENDED_CLOSED to AuthorActionPlacement("ENDED", false, "CLOSED", retained),
            "rejected Exchange, closed request" to AuthorActionPlacement("REJECTED", false, "CLOSED", retained),
            "rescinded Exchange, closed request" to AuthorActionPlacement("RESCINDED", false, "CLOSED", retained),
            "deleted Exchange, open request" to AuthorActionPlacement(
                "ACCEPTED_STARTED",
                true,
                "ISSUED",
                InformationRequestAuthorActions()
            ),
            "draft Exchange, open request" to AuthorActionPlacement(
                "INITIATED",
                false,
                "ISSUED",
                open.copy(promoteFacts = false, revokeFacts = false, recordDecisions = false),
            ),
        )

        @JvmStatic
        fun placements(): List<String> = PLACEMENTS.keys.toList()
    }
}
