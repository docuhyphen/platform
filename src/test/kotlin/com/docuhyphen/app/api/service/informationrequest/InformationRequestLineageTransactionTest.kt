package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.migration.SubmissionRuntimeSqlFixture
import com.docuhyphen.app.api.model.entity.InformationRequestAttestationDecision
import com.docuhyphen.app.api.model.entity.InformationRequestCarryForwardDecision
import com.docuhyphen.app.api.model.entity.InformationRequestLineageKind
import com.docuhyphen.app.api.model.entity.InformationRequestRecurrenceUnit
import com.docuhyphen.app.api.model.informationrequest.CreateInformationRequestSuccessorCommand
import com.docuhyphen.app.api.model.informationrequest.CreateNextInformationRequestOccurrenceCommand
import com.docuhyphen.app.api.model.informationrequest.DefineInformationRequestRecurrenceCommand
import com.docuhyphen.app.api.model.informationrequest.DefineInformationRequestRefreshRuleCommand
import com.docuhyphen.app.api.model.informationrequest.RecordInformationRequestSubmissionAttestationCommand
import com.docuhyphen.app.api.model.informationrequest.RefreshInformationRequestCommand
import com.docuhyphen.app.api.model.informationrequest.SubmitInformationRequestPackageCommand
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestPartyRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestResponseRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTransitionRepository
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.command.CommandPrecondition
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestLineageTransactionTest
{
    @Inject lateinit var dataSource: DataSource
    @Inject lateinit var runtime: InformationRequestRuntimeTestServices
    @Inject lateinit var requestRepository: InformationRequestRepository
    @Inject lateinit var partyRepository: InformationRequestPartyRepository
    @Inject lateinit var responseRepository: InformationRequestResponseRepository
    @Inject lateinit var transitionRepository: InformationRequestTransitionRepository

    @Test
    fun `a supplement preserves the closed source package and asks afresh for evidence and assent`()
    {
        val fixture = fixture()
        val services = runtime.build(fixture.requestId)
        val packageId = submitWhole(services, fixture)

        val command = successor(fixture, InformationRequestLineageKind.SUPPLEMENT, "supplement-once")
        val result = QuarkusTransaction.requiringNew().call { services.successors.create(command) }

        assertEquals(InformationRequestState.CLOSED, result.source.state)
        assertEquals(InformationRequestState.DRAFT, result.successor.state)
        assertEquals(packageId, result.lineage.sourcePackageId)
        assertEquals(
            setOf(
                InformationRequestCarryForwardDecision.INVALIDATED to InformationRequestCarryForwardPlanner.EVIDENCE_REQUIRES_FRESH_COLLECTION,
                InformationRequestCarryForwardDecision.INVALIDATED to InformationRequestCarryForwardPlanner.ATTESTATION_REQUIRES_FRESH_ASSENT,
            ),
            result.carryForwards.map { it.decision to it.reasonCode }.toSet(),
        )
        QuarkusTransaction.requiringNew().run {
            assertEquals(
                setOf(fixture.contributorUserId, fixture.attestorUserId),
                partyRepository.findActiveForRequest(result.successor.id).mapNotNull { it.principalId }.toSet(),
            )
            assertTrue(responseRepository.findAllForRequest(result.successor.id).isEmpty())
            assertTrue(InformationRequestMutation.CREATE_SUCCESSOR in transitionRepository.findForRequest(fixture.requestId).map { it.mutation })
            assertEquals(1, runtime.packageReader.views(fixture.requestId).size)
        }
        val replayed = QuarkusTransaction.requiringNew().call { services.successors.create(command) }
        assertEquals(result.successor.id, replayed.successor.id)
        val lineage = QuarkusTransaction.requiringNew().call { services.lineageQueries.lineage(fixture.requestId, owner(fixture)) }
        assertEquals(listOf(result.successor.id), lineage.successors.map { it.successorRequestId })
    }

    @Test
    fun `a superseding request supersedes an open source and cancellation keeps a submitted stage readable`()
    {
        val superseded = fixture(staged = true)
        val services = runtime.build(superseded.requestId)
        submitStage(services, superseded)

        val result = QuarkusTransaction.requiringNew().call {
            services.successors.create(successor(superseded, InformationRequestLineageKind.SUPERSEDING, "supersede-open"))
        }

        assertEquals(InformationRequestState.SUPERSEDED, result.source.state)
        assertEquals(result.successor.id, result.source.supersededByRequestId)
        QuarkusTransaction.requiringNew().run {
            assertEquals(1, runtime.packageReader.views(superseded.requestId).size)
        }

        val cancelled = fixture(staged = true)
        val cancelServices = runtime.build(cancelled.requestId)
        submitStage(cancelServices, cancelled)
        val outcome = QuarkusTransaction.requiringNew().call {
            cancelServices.lifecycle.cancel(
                CancelInformationRequestCommand(
                    requestId = cancelled.requestId,
                    reasonCode = "no-longer-needed",
                    access = owner(cancelled),
                    precondition = CommandPrecondition.ExpectedRevision(aggregateETag(cancelled.requestId)),
                    idempotencyKey = "cancel-with-stage",
                ),
            )
        }
        assertEquals(InformationRequestState.CANCELLED, outcome.request.state)
        QuarkusTransaction.requiringNew().run {
            assertEquals("record-stage", runtime.packageReader.views(cancelled.requestId).single().submissionPackage.stageKey)
        }
    }

    @Test
    fun `a recurrence creates an occurrence only once it is due and never past its last`()
    {
        val fixture = fixture()
        val services = runtime.build(fixture.requestId)
        submitWhole(services, fixture)
        val recurrence = QuarkusTransaction.requiringNew().call {
            services.followUps.defineRecurrence(
                DefineInformationRequestRecurrenceCommand(
                    requestId = fixture.requestId,
                    intervalUnit = InformationRequestRecurrenceUnit.MONTH,
                    intervalCount = 1,
                    firstDueAt = Instant.now().minus(Duration.ofDays(1)),
                    maximumOccurrences = 2,
                    access = owner(fixture),
                    precondition = CommandPrecondition.ExpectedRevision(aggregateETag(fixture.requestId)),
                    idempotencyKey = "monthly",
                ),
            )
        }

        val first = QuarkusTransaction.requiringNew().call {
            services.followUps.createNextOccurrence(
                CreateNextInformationRequestOccurrenceCommand(fixture.requestId, recurrence.id, owner(fixture), "first-occurrence"),
            )
        }
        assertEquals(InformationRequestLineageKind.RECURRENCE, first.lineage.lineageKind)
        assertEquals(1, first.lineage.recurrenceSequence)

        val early = assertThrows(InformationRequestLifecycleException::class.java)
        {
            QuarkusTransaction.requiringNew().call {
                services.followUps.createNextOccurrence(
                    CreateNextInformationRequestOccurrenceCommand(fixture.requestId, recurrence.id, owner(fixture), "second-occurrence"),
                )
            }
        }
        assertEquals(InformationRequestErrorCatalog.RECURRENCE_NOT_DUE, early.reasonCode)
        assertEquals(
            Instant.now().minus(Duration.ofDays(1)).atZone(ZoneOffset.UTC).plusMonths(1).toLocalDate(),
            services.followUps.dueAt(recurrence, 2).atZone(ZoneOffset.UTC).toLocalDate(),
        )
    }

    @Test
    fun `a refresh rule names a requested document and its refresh follows the request that holds it`()
    {
        val fixture = fixture()
        val services = runtime.build(fixture.requestId)
        submitWhole(services, fixture)

        val refused = assertThrows(InformationRequestLifecycleException::class.java)
        {
            QuarkusTransaction.requiringNew().call { services.followUps.defineRefreshRule(rule(fixture, "recorded-assertion", "assertion-rule")) }
        }
        assertEquals(InformationRequestErrorCatalog.SUCCESSOR_SOURCE_INVALID, refused.reasonCode)
        val rule = QuarkusTransaction.requiringNew().call { services.followUps.defineRefreshRule(rule(fixture, "supporting-record", "record-rule")) }

        val refreshed = QuarkusTransaction.requiringNew().call {
            services.followUps.refresh(RefreshInformationRequestCommand(fixture.requestId, rule.id, owner(fixture), "refresh-once"))
        }

        assertEquals(InformationRequestLineageKind.REFRESH, refreshed.lineage.lineageKind)
        assertEquals(rule.id, refreshed.lineage.refreshRuleId)
        assertTrue(refreshed.carryForwards.all { it.decision == InformationRequestCarryForwardDecision.INVALIDATED })
    }

    private fun fixture(staged: Boolean = false): SubmissionRuntimeSqlFixture =
        dataSource.connection.use { connection -> SubmissionRuntimeSqlFixture(connection, staged = staged) }

    private fun submitWhole(services: InformationRequestRuntimeServices, fixture: SubmissionRuntimeSqlFixture): UUID
    {
        val attested = QuarkusTransaction.requiringNew().call {
            services.attestations.record(
                RecordInformationRequestSubmissionAttestationCommand(
                    requestId = fixture.requestId,
                    requirementId = fixture.attestationRequirementId,
                    decision = InformationRequestAttestationDecision.ASSENTED,
                    access = attestor(fixture),
                    precondition = CommandPrecondition.ExpectedRevision(submissionETag(fixture, null)),
                    idempotencyKey = "assent-${UUID.randomUUID()}",
                ),
            )
        }
        return QuarkusTransaction.requiringNew().call {
            services.submissions.submit(submit(fixture, null, attested.submissionETag))
        }.submission.submissionPackage.id
    }

    private fun submitStage(services: InformationRequestRuntimeServices, fixture: SubmissionRuntimeSqlFixture)
    {
        QuarkusTransaction.requiringNew().run {
            services.submissions.submit(submit(fixture, "record-stage", submissionETag(fixture, "record-stage")))
        }
    }

    private fun submit(fixture: SubmissionRuntimeSqlFixture, stageKey: String?, etag: String) =
        SubmitInformationRequestPackageCommand(
            requestId = fixture.requestId,
            stageKey = stageKey,
            access = contributor(fixture),
            precondition = CommandPrecondition.ExpectedRevision(etag),
            idempotencyKey = "submit-${UUID.randomUUID()}",
        )

    private fun submissionETag(fixture: SubmissionRuntimeSqlFixture, stageKey: String?): String =
        QuarkusTransaction.requiringNew().call {
            val request = requireNotNull(requestRepository.findById(fixture.requestId))
            InformationRequestETag.submissionOf(stageKey, runtime.contentCollector.collect(request, stageKey).contentHash)
        }

    private fun aggregateETag(requestId: UUID): String =
        QuarkusTransaction.requiringNew().call { InformationRequestETag.aggregateOf(requireNotNull(requestRepository.findById(requestId))) }

    private fun successor(fixture: SubmissionRuntimeSqlFixture, kind: InformationRequestLineageKind, key: String) =
        CreateInformationRequestSuccessorCommand(
            sourceRequestId = fixture.requestId,
            kind = kind,
            reasonCode = "additional-information",
            access = owner(fixture),
            precondition = CommandPrecondition.ExpectedRevision(aggregateETag(fixture.requestId)),
            idempotencyKey = key,
        )

    private fun rule(fixture: SubmissionRuntimeSqlFixture, requirementKey: String, key: String) =
        DefineInformationRequestRefreshRuleCommand(
            requestId = fixture.requestId,
            requirementKey = requirementKey,
            leadDays = 30,
            access = owner(fixture),
            precondition = CommandPrecondition.ExpectedRevision(aggregateETag(fixture.requestId)),
            idempotencyKey = key,
        )

    private fun owner(fixture: SubmissionRuntimeSqlFixture) =
        RequestAccessContext(PrincipalRef.user(fixture.template.userId), AuthorizationContext(sessionRef = "owner-session"))

    private fun contributor(fixture: SubmissionRuntimeSqlFixture) =
        RequestAccessContext(PrincipalRef.user(fixture.contributorUserId), AuthorizationContext(sessionRef = "contributor-session"))

    private fun attestor(fixture: SubmissionRuntimeSqlFixture) =
        RequestAccessContext(PrincipalRef.user(fixture.attestorUserId), AuthorizationContext(sessionRef = "attestor-session"))
}
