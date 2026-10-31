package com.docuhyphen.app.api.service.informationrequest.audit

import com.docuhyphen.app.api.migration.SubmissionRuntimeSqlFixture
import com.docuhyphen.app.api.model.command.*
import com.docuhyphen.app.api.model.entity.InformationRequestRecurrenceUnit
import com.docuhyphen.app.api.model.informationrequest.InformationRequestAuditActor
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.lifecycle.DefineInformationRequestRecurrenceCommand
import com.docuhyphen.app.api.model.informationrequest.lifecycle.DefineInformationRequestRefreshRuleCommand
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestMutation
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestTransitionHistoryCommand
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.service.audit.catalog.AuditEventType
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.command.CommandRequestFingerprint
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.InformationRequestRuntimeTestServices
import com.docuhyphen.app.api.service.informationrequest.audit.InformationRequestAuditReconciliation.assertReconciled
import com.docuhyphen.app.api.service.informationrequest.audit.InformationRequestAuditReconciliation.grantRequestShare
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestAuditOperationScopeTransactionTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var runtime: InformationRequestRuntimeTestServices

    @Inject
    lateinit var requests: InformationRequestRepository

    @Inject
    lateinit var authorization: AuthorizationService

    @Test
    fun `one manager defining a recurrence and a refresh rule under the same client key keeps both audit events`() {
        defineFollowUps(SHARED_KEY, SHARED_KEY, retry = false)
    }

    @Test
    fun `one manager defining a recurrence and a refresh rule under different client keys keeps both audit events`() {
        defineFollowUps("recurrence-key", "refresh-key", retry = false)
    }

    @Test
    fun `retrying both follow-up definitions under their own client keys keeps exactly their two audit events`() {
        defineFollowUps("recurrence-key", "refresh-key", retry = true)
    }

    @Test
    fun `callers of different kinds sharing an identifier and a client key each keep their own audit event`() {
        val fixture = dataSource.connection.use { SubmissionRuntimeSqlFixture(it) }
        val callers =
            listOf(PrincipalRef.user(fixture.contributorUserId), PrincipalRef.participant(fixture.contributorUserId))

        val decisions = callers.map { caller -> recordDirectly(fixture, caller) }
        val retried = recordDirectly(fixture, callers.first())

        decisions.forEach { assertInstanceOf(CommandReceiptDecision.Recorded::class.java, it) }
        assertInstanceOf(CommandReceiptDecision.Replayed::class.java, retried)
        assertReconciled(
            dataSource,
            fixture.requestId,
            InformationRequestMutation.SAVE_RESPONSE.name,
            AuditEventType.INFORMATION_REQUEST_REQUIREMENT_RESPOND.key,
            2,
        )
    }

    private fun defineFollowUps(recurrenceKey: String, refreshKey: String, retry: Boolean) {
        val managerParty = UUID.randomUUID()
        val fixture = dataSource.connection.use { connection ->
            SubmissionRuntimeSqlFixture(connection, evidenceExpiresOn = LocalDate.now().plusDays(30)).also {
                it.insertPartyForUser(managerParty, DECISION_MAKER, it.contributorUserId)
            }
        }
        grantRequestShare(dataSource, fixture, managerParty, DECISION_MAKER, fixture.contributorUserId)
        val services = runtime.build(fixture.requestId, centralAuthorization = authorization)
        val access = RequestAccessContext(
            PrincipalRef.user(fixture.contributorUserId),
            AuthorizationContext(sessionRef = "follow-up-manager"),
        )
        val recurrence = DefineInformationRequestRecurrenceCommand(
            requestId = fixture.requestId,
            intervalUnit = InformationRequestRecurrenceUnit.DAY,
            intervalCount = 1,
            firstDueAt = Instant.now().plusSeconds(3600),
            access = access,
            precondition = aggregatePrecondition(fixture.requestId),
            idempotencyKey = recurrenceKey,
        )
        QuarkusTransaction.requiringNew().run { services.followUps.defineRecurrence(recurrence) }
        val refresh = DefineInformationRequestRefreshRuleCommand(
            requestId = fixture.requestId,
            requirementKey = "supporting-record",
            leadDays = 1,
            access = access,
            precondition = aggregatePrecondition(fixture.requestId),
            idempotencyKey = refreshKey,
        )
        QuarkusTransaction.requiringNew().run { services.followUps.defineRefreshRule(refresh) }
        if (retry) {
            QuarkusTransaction.requiringNew().run { services.followUps.defineRecurrence(recurrence) }
            QuarkusTransaction.requiringNew().run { services.followUps.defineRefreshRule(refresh) }
        }

        assertReconciled(
            dataSource,
            fixture.requestId,
            InformationRequestMutation.SCHEDULE_FOLLOW_UP.name,
            AuditEventType.INFORMATION_REQUEST_FOLLOW_UP_SCHEDULE.key,
            2,
        )
    }

    private fun recordDirectly(
        fixture: SubmissionRuntimeSqlFixture,
        caller: PrincipalRef
    ): CommandReceiptDecision<Unit> {
        val receipt = CommandReceiptRequest(
            resource = ResourceRef.informationRequest(fixture.requestId),
            operation = DIRECT_OPERATION,
            actor = CommandActorRef.principal(caller),
            idempotencyKey = SHARED_KEY,
            requestFingerprint = CommandRequestFingerprint.of(DIRECT_OPERATION, fixture.requestId),
        )
        return QuarkusTransaction.requiringNew().call {
            runtime.commandReceiptService.runOnce(receipt) {
                val request = requireNotNull(requests.findById(fixture.requestId))
                runtime.transitionHistory.record(
                    InformationRequestTransitionHistoryCommand(
                        request = request,
                        fromState = request.state,
                        toState = request.state,
                        mutation = InformationRequestMutation.SAVE_RESPONSE,
                        actor = InformationRequestAuditActor.of(
                            RequestAccessContext(caller, AuthorizationContext(sessionRef = "direct-${caller.kind}")),
                        ),
                        auditIdempotencyKey = "information_request.response|${request.id}|$SHARED_KEY",
                    ),
                )
                CommandMutationResult(Unit, CommandResultReference(receipt.resource.type, request.id))
            }
        }
    }

    private fun aggregatePrecondition(requestId: UUID) = CommandPrecondition.ExpectedRevision(
        QuarkusTransaction.requiringNew().call {
            InformationRequestETag.aggregateOf(requireNotNull(requests.findById(requestId)))
        },
    )

    private companion object {
        const val SHARED_KEY = "shared-client-key"
        const val DECISION_MAKER = "DECISION_MAKER"
        const val DIRECT_OPERATION = "record-information-request-response-directly"
    }
}
