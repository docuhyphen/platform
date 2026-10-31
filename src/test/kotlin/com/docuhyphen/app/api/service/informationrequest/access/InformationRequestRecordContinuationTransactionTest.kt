package com.docuhyphen.app.api.service.informationrequest.access

import com.docuhyphen.app.api.migration.*
import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.acceptedfact.PromoteInformationRequestAcceptedFactCommand
import com.docuhyphen.app.api.model.informationrequest.acceptedfact.RecordInformationRequestBusinessDecisionCommand
import com.docuhyphen.app.api.model.informationrequest.acceptedfact.RevokeInformationRequestAcceptedFactCommand
import com.docuhyphen.app.api.model.informationrequest.clock.StartInformationRequestClockCommand
import com.docuhyphen.app.api.model.informationrequest.externalsource.DecideInformationRequestImportedValueCommand
import com.docuhyphen.app.api.model.informationrequest.externalsource.ProposeInformationRequestImportedValueCommand
import com.docuhyphen.app.api.model.informationrequest.externalsource.ReconcileInformationRequestImportedValuesCommand
import com.docuhyphen.app.api.model.informationrequest.lifecycle.ChangeInformationRequestCompletionGateCommand
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.clock.InformationRequestClockEventRepository
import com.docuhyphen.app.api.repository.informationrequest.clock.InformationRequestClockRepository
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestRuntimeTestServices
import com.docuhyphen.app.api.service.informationrequest.access.model.ContinuationFixture
import com.docuhyphen.app.api.service.informationrequest.clock.InformationRequestClockPolicyService
import com.docuhyphen.app.api.service.informationrequest.clock.InformationRequestClockRecorder
import com.docuhyphen.app.api.service.informationrequest.clock.InformationRequestClockService
import com.docuhyphen.app.api.service.informationrequest.execution.InformationRequestExecutionGrantService
import com.docuhyphen.app.api.service.informationrequest.execution.everyContinuationAction
import com.docuhyphen.app.api.service.informationrequest.execution.issuedExecutionGrant
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestCompletionGateService
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestTransitionHistoryService
import com.docuhyphen.app.api.service.informationrequest.model.InformationRequestRuntimeServices
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.sql.Timestamp
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestRecordContinuationTransactionTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var runtime: InformationRequestRuntimeTestServices

    @Inject
    lateinit var requestRepository: InformationRequestRepository

    @Inject
    lateinit var clockRepository: InformationRequestClockRepository

    @Inject
    lateinit var eventRepository: InformationRequestClockEventRepository

    @Inject
    lateinit var policies: InformationRequestClockPolicyService

    @Inject
    lateinit var recorder: InformationRequestClockRecorder

    @Inject
    lateinit var transitionHistory: InformationRequestTransitionHistoryService

    @Test
    fun `record changes that add to a request are refused once its execution grant is revoked`() {
        val fixture = fixture()
        val active = runtime.build(fixture.requestId)
        val proposed = QuarkusTransaction.requiringNew()
            .call { active.importedValueIntake.propose(proposal(fixture, "propose-before-revocation")) }.value.id
        val revoked = runtime.build(
            fixture.requestId,
            executionGrants = grants(fixture.requestId, revoked = true, actions = everyContinuationAction)
        )

        val refusals = listOf(
            refusal { revoked.acceptedFacts.promote(promotion(fixture, "promote-after-revocation")) },
            refusal { revoked.businessDecisions.record(decision(fixture, "decide-after-revocation")) },
            refusal { revoked.importedValueIntake.propose(proposal(fixture, "propose-after-revocation")) },
            refusal {
                revoked.importedValueDecisions.decide(
                    DecideInformationRequestImportedValueCommand(
                        fixture.requestId,
                        proposed,
                        InformationRequestImportedValueDecisionKind.REJECTED,
                        "does not match",
                        owner(fixture),
                        "reject-after-revocation",
                    ),
                )
            },
            refusal {
                revoked.importedValueReconciliation.reconcile(
                    ReconcileInformationRequestImportedValuesCommand(
                        fixture.requestId,
                        owner(fixture),
                        "reconcile-after-revocation"
                    ),
                )
            },
            refusal { clocks(revoked).start(clockStart(fixture, "start-after-revocation")) },
        )

        assertEquals(List(refusals.size) { InformationRequestErrorCatalog.EXECUTION_GRANT_REVOKED }, refusals)
    }

    @Test
    fun `a fact stays revocable after the request's execution grant is revoked`() {
        val fixture = fixture()
        val promoted = QuarkusTransaction.requiringNew().call {
            runtime.build(fixture.requestId).acceptedFacts.promote(promotion(fixture, "promote-before-revocation"))
        }
        val revoked = runtime.build(
            fixture.requestId,
            executionGrants = grants(fixture.requestId, revoked = true, actions = everyContinuationAction)
        )

        QuarkusTransaction.requiringNew().run {
            revoked.acceptedFacts.revoke(
                RevokeInformationRequestAcceptedFactCommand(
                    requestId = fixture.requestId,
                    factId = promoted.fact.id,
                    reasonCode = "no-longer-accurate",
                    access = owner(fixture),
                    idempotencyKey = "revoke-after-revocation",
                ),
            )
        }

        assertEquals(
            1,
            dataSource.connection.use { connection ->
                queryInt(
                    connection,
                    "SELECT COUNT(*) FROM information_request_accepted_fact_revocation WHERE fact_id = ?",
                    promoted.fact.id
                )
            },
        )
    }

    @Test
    fun `changing the completion gate is an owner recovery that needs a grant permitting cancellation`() {
        val fixture = fixture()
        val withoutCancellation = runtime.build(
            fixture.requestId,
            executionGrants = grants(fixture.requestId, revoked = true, actions = everyContinuationAction - "CANCEL"),
        )
        val recovering = runtime.build(
            fixture.requestId,
            executionGrants = grants(fixture.requestId, revoked = true, actions = everyContinuationAction)
        )

        val refused =
            refusal { completionGate(withoutCancellation).change(gateChange(fixture, "gate-without-cancellation")) }
        QuarkusTransaction.requiringNew()
            .run { completionGate(recovering).change(gateChange(fixture, "gate-as-recovery")) }

        assertEquals(InformationRequestErrorCatalog.CONTINUATION_NOT_GRANTED, refused)
    }

    private fun refusal(block: () -> Any?): String =
        assertThrows(InformationRequestLifecycleException::class.java) {
            QuarkusTransaction.requiringNew().call { block() }
        }.reasonCode

    private fun fixture(): ContinuationFixture = dataSource.connection.use { connection ->
        lateinit var answers: FieldAnswerSqlFixture
        val runtimeFixture = SubmissionRuntimeSqlFixture(
            connection,
            beforePublish = { configured ->
                answers = FieldAnswerSqlFixture(connection, configured.template, reusePurpose = "profile.reuse")
            },
        )
        answers.materialize(runtimeFixture)
        val facts = FactSqlFixture(connection, runtimeFixture, answers)
        val clocks = ClockSqlFixture(connection, runtimeFixture)
        ContinuationFixture(runtimeFixture, answers, facts, clocks.versionId)
    }

    private fun grants(
        requestId: UUID,
        revoked: Boolean,
        actions: List<String>
    ): InformationRequestExecutionGrantService =
        mock<InformationRequestExecutionGrantService>().also { grants ->
            whenever(grants.findForRequest(requestId)).thenReturn(
                issuedExecutionGrant().apply {
                    this.requestId = requestId
                    ownerType = "ORGANIZATION"
                    planCode = "BUSINESS"
                    subscriptionStatus = "ACTIVE"
                    enforcementMode = "ENFORCE"
                    issuedAt = Timestamp.from(Instant.now().minus(Duration.ofDays(1)))
                    continuationActions = actions.toTypedArray()
                    if (revoked) {
                        revokedAt = Timestamp.from(Instant.now())
                        revokedReason = "stopped"
                    }
                },
            )
        }

    private fun promotion(fixture: ContinuationFixture, key: String) = PromoteInformationRequestAcceptedFactCommand(
        requestId = fixture.requestId,
        packageId = fixture.facts.packageId,
        submissionItemId = fixture.facts.itemId,
        purposeKey = "profile.reuse",
        policyBasisKey = "policy.reuse",
        visibility = InformationRequestAcceptedFactVisibility.RESPONDING_PARTIES,
        validTo = Instant.now().plus(Duration.ofDays(365)),
        access = owner(fixture),
        idempotencyKey = key,
    )

    private fun decision(fixture: ContinuationFixture, key: String) = RecordInformationRequestBusinessDecisionCommand(
        requestId = fixture.requestId,
        owningProcessKey = "process.outcome",
        outcomeCode = "outcome.recorded",
        reasonReference = "reference-1",
        kind = InformationRequestBusinessDecisionKind.ORIGINAL,
        priorDecisionId = null,
        decidedAt = Instant.now().minus(Duration.ofMinutes(5)),
        access = owner(fixture),
        idempotencyKey = key,
    )

    private fun proposal(fixture: ContinuationFixture, key: String) = ProposeInformationRequestImportedValueCommand(
        requestId = fixture.requestId,
        requirementId = fixture.answers.requirementId,
        resultKey = "registry-value",
        valueType = FieldValueType.SHORT_TEXT,
        value = JsonPrimitive("Registry value"),
        sourceReference = "registry-extract",
        confidence = InformationRequestSourceConfidence.ASSERTED,
        verifiedAt = null,
        expiresAt = null,
        provenanceReference = "extract-1",
        access = owner(fixture),
        idempotencyKey = key,
    )

    private fun clocks(services: InformationRequestRuntimeServices) = InformationRequestClockService(
        services.gate,
        clockRepository,
        eventRepository,
        policies,
        recorder,
        runtime.commandReceiptService,
        Clock.systemUTC(),
    )

    private fun clockStart(fixture: ContinuationFixture, key: String) = StartInformationRequestClockCommand(
        requestId = fixture.requestId,
        clockKey = "response",
        policyVersionId = fixture.clockPolicyVersionId,
        urgency = InformationRequestClockUrgency.STANDARD,
        receivedAt = Instant.now(),
        access = owner(fixture),
        idempotencyKey = key,
    )

    private fun completionGate(services: InformationRequestRuntimeServices) =
        InformationRequestCompletionGateService(
            services.gate,
            requestRepository,
            runtime.commandReceiptService,
            transitionHistory,
            Clock.systemUTC()
        )

    private fun gateChange(fixture: ContinuationFixture, key: String) = ChangeInformationRequestCompletionGateCommand(
        requestId = fixture.requestId,
        gatesExchangeClosure = false,
        access = owner(fixture),
        precondition = CommandPrecondition.ExpectedRevision(
            QuarkusTransaction.requiringNew()
                .call { InformationRequestETag.aggregateOf(requireNotNull(requestRepository.findById(fixture.requestId))) },
        ),
        idempotencyKey = key,
    )

    private fun owner(fixture: ContinuationFixture) =
        RequestAccessContext(
            PrincipalRef.user(fixture.runtime.template.userId),
            AuthorizationContext(sessionRef = "owner-session", mfaSatisfied = true)
        )
}
