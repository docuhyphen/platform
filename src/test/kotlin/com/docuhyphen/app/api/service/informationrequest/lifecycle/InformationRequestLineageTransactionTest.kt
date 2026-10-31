package com.docuhyphen.app.api.service.informationrequest.lifecycle

import com.docuhyphen.app.api.migration.*
import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.lifecycle.*
import com.docuhyphen.app.api.model.informationrequest.submission.RecordInformationRequestSubmissionAttestationCommand
import com.docuhyphen.app.api.model.informationrequest.submission.SubmitInformationRequestPackageCommand
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.lifecycle.InformationRequestTransitionRepository
import com.docuhyphen.app.api.repository.informationrequest.party.InformationRequestPartyRepository
import com.docuhyphen.app.api.repository.informationrequest.response.InformationRequestResponseRepository
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestRuntimeTestServices
import com.docuhyphen.app.api.service.informationrequest.model.InformationRequestRuntimeServices
import com.docuhyphen.app.api.service.informationrequest.response.InformationRequestCarryForwardPlanner
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestLineageTransactionTest
{
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var runtime: InformationRequestRuntimeTestServices

    @Inject
    lateinit var requestRepository: InformationRequestRepository

    @Inject
    lateinit var partyRepository: InformationRequestPartyRepository

    @Inject
    lateinit var responseRepository: InformationRequestResponseRepository

    @Inject
    lateinit var transitionRepository: InformationRequestTransitionRepository

    @Inject
    lateinit var refreshes: InformationRequestRefreshScheduler

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
            val stored = requireNotNull(requestRepository.findById(result.successor.id))
            assertEquals(PrincipalKind.USER, stored.createdByPrincipalKind)
            assertEquals(fixture.template.userId, stored.createdByPrincipalId)
            assertEquals(
                setOf(PrincipalKind.USER to fixture.template.userId),
                partyRepository.findActiveForRequest(result.successor.id)
                    .map { it.assignedByPrincipalKind to it.assignedByPrincipalId }.toSet(),
            )
            assertTrue(responseRepository.findAllForRequest(result.successor.id).isEmpty())
            assertTrue(
                InformationRequestMutation.CREATE_SUCCESSOR in transitionRepository.findForRequest(fixture.requestId)
                    .map { it.mutation })
            assertEquals(1, runtime.packageReader.views(fixture.requestId).size)
        }
        val replayed = QuarkusTransaction.requiringNew().call { services.successors.create(command) }
        assertEquals(result.successor.id, replayed.successor.id)
        val lineage = QuarkusTransaction.requiringNew()
            .call { services.lineageQueries.lineage(fixture.requestId, owner(fixture)) }
        assertEquals(listOf(result.successor.id), lineage.successors.map { it.successorRequestId })
    }

    @Test
    fun `a follow-up of a staged source carries forward from every submitted stage and preserves each stage package`() {
        val fixture = fixture(staged = true)
        val services = runtime.build(fixture.requestId)
        val recordPackage = QuarkusTransaction.requiringNew().call {
            services.submissions.submit(submit(fixture, "record-stage", submissionETag(fixture, "record-stage")))
        }.submission.submissionPackage.id
        val attested = QuarkusTransaction.requiringNew().call {
            services.attestations.record(
                RecordInformationRequestSubmissionAttestationCommand(
                    requestId = fixture.requestId,
                    requirementId = fixture.attestationRequirementId,
                    decision = InformationRequestAttestationDecision.ASSENTED,
                    access = attestor(fixture),
                    precondition = CommandPrecondition.ExpectedRevision(submissionETag(fixture, "confirmation-stage")),
                    idempotencyKey = "assent-${UUID.randomUUID()}",
                ),
            )
        }
        val confirmationPackage = QuarkusTransaction.requiringNew().call {
            services.submissions.submit(submit(fixture, "confirmation-stage", attested.submissionETag))
        }.submission.submissionPackage.id

        val result = QuarkusTransaction.requiringNew().call {
            services.successors.create(
                successor(
                    fixture,
                    InformationRequestLineageKind.SUPPLEMENT,
                    "supplement-staged"
                )
            )
        }

        assertEquals(confirmationPackage, result.lineage.sourcePackageId)
        assertEquals(
            setOf(
                recordPackage to InformationRequestCarryForwardPlanner.EVIDENCE_REQUIRES_FRESH_COLLECTION,
                confirmationPackage to InformationRequestCarryForwardPlanner.ATTESTATION_REQUIRES_FRESH_ASSENT,
            ),
            result.carryForwards.map { it.sourcePackageId to it.reasonCode }.toSet(),
        )
        assertEquals(setOf(recordPackage, confirmationPackage), preservedPackages(result.lineage.id))
        val replayed = QuarkusTransaction.requiringNew().call {
            services.successors.create(
                successor(
                    fixture,
                    InformationRequestLineageKind.SUPPLEMENT,
                    "supplement-staged"
                )
            )
        }
        assertEquals(result.carryForwards.map { it.id }.toSet(), replayed.carryForwards.map { it.id }.toSet())
    }

    @Test
    fun `a follow-up keeps the subject its source request is about`()
    {
        val subjectId = UUID.randomUUID()
        val fixture = dataSource.connection.use { connection ->
            SubmissionRuntimeSqlFixture(connection).also { it.insertSubject(subjectId, UUID.randomUUID()) }
        }
        val services = runtime.build(fixture.requestId)
        submitWhole(services, fixture)

        val result = QuarkusTransaction.requiringNew().call {
            services.successors.create(
                successor(
                    fixture,
                    InformationRequestLineageKind.SUPPLEMENT,
                    "supplement-subject"
                )
            )
        }

        QuarkusTransaction.requiringNew().run {
            assertEquals(
                listOf(subjectId),
                partyRepository.findActiveForRequestRole(result.successor.id, InformationRequestShareRoleKey.SUBJECT)
                    .map { it.subjectIdentityRefId },
            )
        }
    }

    @Test
    fun `a superseding request supersedes an open source and cancellation keeps a submitted stage readable`()
    {
        val superseded = fixture(staged = true)
        val services = runtime.build(superseded.requestId)
        submitStage(services, superseded)

        val result = QuarkusTransaction.requiringNew().call {
            services.successors.create(
                successor(
                    superseded,
                    InformationRequestLineageKind.SUPERSEDING,
                    "supersede-open"
                )
            )
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
            assertEquals(
                "record-stage",
                runtime.packageReader.views(cancelled.requestId).single().submissionPackage.stageKey
            )
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
            ).recurrence
        }

        val scheduled = QuarkusTransaction.requiringNew()
            .call { services.lineageQueries.lineage(fixture.requestId, owner(fixture)) }
        assertEquals(recurrence.id, scheduled.recurrence?.id)
        assertEquals(services.followUps.dueAt(requireNotNull(scheduled.recurrence), 1), scheduled.nextOccurrenceDueAt)

        val first = QuarkusTransaction.requiringNew().call {
            services.followUps.createNextOccurrence(
                CreateNextInformationRequestOccurrenceCommand(
                    fixture.requestId,
                    recurrence.id,
                    owner(fixture),
                    "first-occurrence"
                ),
            )
        }
        assertEquals(InformationRequestLineageKind.RECURRENCE, first.lineage.lineageKind)
        assertEquals(1, first.lineage.recurrenceSequence)
        val afterFirst = QuarkusTransaction.requiringNew()
            .call { services.lineageQueries.lineage(fixture.requestId, owner(fixture)) }
        assertEquals(services.followUps.dueAt(requireNotNull(afterFirst.recurrence), 2), afterFirst.nextOccurrenceDueAt)

        val early = assertThrows(InformationRequestLifecycleException::class.java)
        {
            QuarkusTransaction.requiringNew().call {
                services.followUps.createNextOccurrence(
                    CreateNextInformationRequestOccurrenceCommand(
                        fixture.requestId,
                        recurrence.id,
                        owner(fixture),
                        "second-occurrence"
                    ),
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
    fun `a recurrence that has created every occurrence it allows says it is complete`() {
        val fixture = fixture()
        val services = runtime.build(fixture.requestId)
        submitWhole(services, fixture)
        val recurrence = QuarkusTransaction.requiringNew().call {
            services.followUps.defineRecurrence(
                DefineInformationRequestRecurrenceCommand(
                    requestId = fixture.requestId,
                    intervalUnit = InformationRequestRecurrenceUnit.MONTH,
                    intervalCount = 1,
                    firstDueAt = Instant.now().minus(Duration.ofDays(70)),
                    maximumOccurrences = 1,
                    access = owner(fixture),
                    precondition = CommandPrecondition.ExpectedRevision(aggregateETag(fixture.requestId)),
                    idempotencyKey = "single-occurrence",
                ),
            ).recurrence
        }
        QuarkusTransaction.requiringNew().call {
            services.followUps.createNextOccurrence(
                CreateNextInformationRequestOccurrenceCommand(
                    fixture.requestId,
                    recurrence.id,
                    owner(fixture),
                    "only-occurrence"
                ),
            )
        }

        val complete = assertThrows(InformationRequestLifecycleException::class.java) {
            QuarkusTransaction.requiringNew().call {
                services.followUps.createNextOccurrence(
                    CreateNextInformationRequestOccurrenceCommand(
                        fixture.requestId,
                        recurrence.id,
                        owner(fixture),
                        "beyond-the-series"
                    ),
                )
            }
        }

        assertEquals(InformationRequestErrorCatalog.RECURRENCE_COMPLETE, complete.reasonCode)
    }

    @ParameterizedTest
    @CsvSource("CANCELLED, cancelled_at", "SUPERSEDED, superseded_at")
    fun `a recurrence continues from its origin after its latest occurrence is cancelled or superseded`(
        state: String,
        finishedAt: String
    ) {
        val fixture = fixture()
        val services = runtime.build(fixture.requestId)
        submitWhole(services, fixture)
        val recurrence = QuarkusTransaction.requiringNew().call {
            services.followUps.defineRecurrence(
                DefineInformationRequestRecurrenceCommand(
                    requestId = fixture.requestId,
                    intervalUnit = InformationRequestRecurrenceUnit.MONTH,
                    intervalCount = 1,
                    firstDueAt = Instant.now().minus(Duration.ofDays(70)),
                    maximumOccurrences = 3,
                    access = owner(fixture),
                    precondition = CommandPrecondition.ExpectedRevision(aggregateETag(fixture.requestId)),
                    idempotencyKey = "monthly-with-a-finished-occurrence",
                ),
            ).recurrence
        }
        val first = QuarkusTransaction.requiringNew().call {
            services.followUps.createNextOccurrence(
                CreateNextInformationRequestOccurrenceCommand(
                    fixture.requestId,
                    recurrence.id,
                    owner(fixture),
                    "first-occurrence"
                ),
            )
        }
        dataSource.connection.use { connection ->
            execute(
                connection,
                "UPDATE information_request SET state = ?, $finishedAt = now() WHERE id = ?",
                state,
                first.successor.id,
            )
        }

        val second = QuarkusTransaction.requiringNew().call {
            services.followUps.createNextOccurrence(
                CreateNextInformationRequestOccurrenceCommand(
                    fixture.requestId,
                    recurrence.id,
                    owner(fixture),
                    "second-occurrence"
                ),
            )
        }

        assertEquals(2, second.lineage.recurrenceSequence)
        assertEquals(fixture.requestId, second.source.id)
        assertNotEquals(first.successor.id, second.successor.id)
    }

    @Test
    fun `an occurrence shows its series, which continues from any open occurrence after its origin is cancelled`()
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
                    firstDueAt = Instant.now().minus(Duration.ofDays(70)),
                    maximumOccurrences = 3,
                    access = owner(fixture),
                    precondition = CommandPrecondition.ExpectedRevision(aggregateETag(fixture.requestId)),
                    idempotencyKey = "series-after-cancelled-origin",
                ),
            ).recurrence
        }
        val first = QuarkusTransaction.requiringNew().call {
            services.followUps.createNextOccurrence(
                CreateNextInformationRequestOccurrenceCommand(
                    fixture.requestId,
                    recurrence.id,
                    owner(fixture),
                    "series-first"
                ),
            )
        }
        dataSource.connection.use { connection ->
            execute(
                connection,
                "UPDATE information_request SET state = 'ISSUED', issued_at = now() WHERE id = ?",
                first.successor.id
            )
            execute(
                connection,
                "UPDATE information_request SET state = 'CANCELLED', cancelled_at = now() WHERE id = ?",
                fixture.requestId
            )
        }

        val occurrence = lineageJson(services, first.successor.id, fixture)
        val origin = lineageJson(services, fixture.requestId, fixture)
        val second = QuarkusTransaction.requiringNew().call {
            services.followUps.createNextOccurrence(
                CreateNextInformationRequestOccurrenceCommand(
                    fixture.requestId,
                    recurrence.id,
                    owner(fixture),
                    "series-second"
                ),
            )
        }
        dataSource.connection.use { connection ->
            execute(
                connection,
                "UPDATE information_request SET state = 'CANCELLED', cancelled_at = now() WHERE id IN (?, ?)",
                first.successor.id,
                second.successor.id
            )
        }
        val ended = lineageJson(services, fixture.requestId, fixture)

        assertEquals(recurrence.id.toString(), occurrence["series"]?.jsonObject?.get("id")?.jsonPrimitive?.content)
        assertEquals(true, occurrence["nextOccurrenceCreatable"]?.jsonPrimitive?.booleanOrNull)
        assertEquals(true, origin["nextOccurrenceCreatable"]?.jsonPrimitive?.booleanOrNull)
        assertEquals(first.successor.id, second.source.id)
        assertEquals(false, ended["nextOccurrenceCreatable"]?.jsonPrimitive?.booleanOrNull ?: false)
    }

    private fun lineageJson(
        services: InformationRequestRuntimeServices,
        requestId: UUID,
        fixture: SubmissionRuntimeSqlFixture
    ) =
        Json.encodeToJsonElement(
            com.docuhyphen.app.api.model.InformationRequestLineageDtoMapper.toDto(
                QuarkusTransaction.requiringNew().call { services.lineageQueries.lineage(requestId, owner(fixture)) },
            ),
        ).jsonObject

    @Test
    fun `a refresh rule names a requested document and its refresh follows the request that holds it`() {
        val fixture = fixture(evidenceExpiresOn = today().plusDays(10))
        val services = runtime.build(fixture.requestId)
        submitWhole(services, fixture)

        val refused = assertThrows(InformationRequestLifecycleException::class.java)
        {
            QuarkusTransaction.requiringNew()
                .call { services.followUps.defineRefreshRule(rule(fixture, "recorded-assertion", "assertion-rule")) }
        }
        assertEquals(InformationRequestErrorCatalog.SUCCESSOR_SOURCE_INVALID, refused.reasonCode)
        val rule = QuarkusTransaction.requiringNew().call {
            services.followUps.defineRefreshRule(
                rule(
                    fixture,
                    "supporting-record",
                    "record-rule"
                )
            ).refreshRule
        }

        val refreshed = QuarkusTransaction.requiringNew().call {
            services.followUps.refresh(
                RefreshInformationRequestCommand(
                    fixture.requestId,
                    rule.id,
                    owner(fixture),
                    "refresh-once"
                )
            )
        }

        assertEquals(InformationRequestLineageKind.REFRESH, refreshed.lineage.lineageKind)
        assertEquals(rule.id, refreshed.lineage.refreshRuleId)
        assertTrue(refreshed.carryForwards.all { it.decision == InformationRequestCarryForwardDecision.INVALIDATED })
        val again = assertThrows(InformationRequestLifecycleException::class.java) {
            QuarkusTransaction.requiringNew().call {
                services.followUps.refresh(
                    RefreshInformationRequestCommand(
                        fixture.requestId,
                        rule.id,
                        owner(fixture),
                        "refresh-twice"
                    )
                )
            }
        }
        assertEquals(InformationRequestErrorCatalog.REFRESH_ALREADY_CREATED, again.reasonCode)
    }

    @Test
    fun `a refresh is refused until the evidence it renews is within the rule's lead time`() {
        val fixture = fixture(evidenceExpiresOn = today().plusDays(90))
        val services = runtime.build(fixture.requestId)
        submitWhole(services, fixture)
        val rule = QuarkusTransaction.requiringNew()
            .call { services.followUps.defineRefreshRule(rule(fixture, "supporting-record", "early-rule")).refreshRule }

        val refused = assertThrows(InformationRequestLifecycleException::class.java) {
            QuarkusTransaction.requiringNew().call {
                services.followUps.refresh(
                    RefreshInformationRequestCommand(
                        fixture.requestId,
                        rule.id,
                        owner(fixture),
                        "refresh-early"
                    )
                )
            }
        }

        assertEquals(InformationRequestErrorCatalog.REFRESH_NOT_DUE, refused.reasonCode)
    }

    @Test
    fun `a refresh rule needs a requested document whose expiry is captured`() {
        val fixture = fixture()
        val services = runtime.build(fixture.requestId)
        submitWhole(services, fixture)

        val refused = assertThrows(InformationRequestLifecycleException::class.java) {
            QuarkusTransaction.requiringNew()
                .call { services.followUps.defineRefreshRule(rule(fixture, "supporting-record", "uncaptured-rule")) }
        }

        assertEquals(InformationRequestErrorCatalog.REFRESH_EXPIRY_NOT_CAPTURED, refused.reasonCode)
    }

    @Test
    fun `the scheduled pass creates each due refresh once and leaves a refresh that is not due`() {
        val due = fixture(evidenceExpiresOn = today().plusDays(10))
        val early = fixture(evidenceExpiresOn = today().plusDays(90))
        listOf(due, early).forEach { fixture ->
            val services = runtime.build(fixture.requestId)
            submitWhole(services, fixture)
            QuarkusTransaction.requiringNew()
                .call { services.followUps.defineRefreshRule(rule(fixture, "supporting-record", "scheduled-rule")) }
        }

        refreshes.processDueRefreshes(Instant.now())
        refreshes.processDueRefreshes(Instant.now())

        assertEquals(1, refreshLineages(due.requestId))
        assertEquals(0, refreshLineages(early.requestId))
        val refresh = QuarkusTransaction.requiringNew().call {
            requireNotNull(requestRepository.findById(requireNotNull(refreshSuccessor(due.requestId))))
        }
        assertEquals(PrincipalKind.SERVICE_ACCOUNT, refresh.createdByPrincipalKind)
        assertEquals(UUID(0, 0), refresh.createdByPrincipalId)
    }

    private fun fixture(staged: Boolean = false, evidenceExpiresOn: LocalDate? = null): SubmissionRuntimeSqlFixture =
        dataSource.connection.use { connection ->
            SubmissionRuntimeSqlFixture(connection, staged = staged, evidenceExpiresOn = evidenceExpiresOn)
        }

    private fun today(): LocalDate = LocalDate.now(ZoneOffset.UTC)

    private fun refreshSuccessor(requestId: UUID): UUID? = dataSource.connection.use { connection ->
        queryString(
            connection,
            "SELECT successor_request_id::text FROM information_request_lineage WHERE source_request_id = ? AND lineage_kind = 'REFRESH'",
            requestId,
        )?.let(UUID::fromString)
    }

    private fun preservedPackages(lineageId: UUID): Set<UUID> = dataSource.connection.use { connection ->
        queryStrings(
            connection,
            "SELECT package_id::text FROM information_request_lineage_source_package WHERE lineage_id = ?",
            lineageId
        )
            .map(UUID::fromString)
            .toSet()
    }

    private fun refreshLineages(requestId: UUID): Int = dataSource.connection.use { connection ->
        queryInt(
            connection,
            "SELECT count(*) FROM information_request_lineage WHERE source_request_id = ? AND lineage_kind = 'REFRESH'",
            requestId,
        )
    }

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
            InformationRequestETag.submissionOf(
                stageKey,
                runtime.contentCollector.collect(request, stageKey).contentHash
            )
        }

    private fun aggregateETag(requestId: UUID): String =
        QuarkusTransaction.requiringNew()
            .call { InformationRequestETag.aggregateOf(requireNotNull(requestRepository.findById(requestId))) }

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
        RequestAccessContext(
            PrincipalRef.user(fixture.template.userId),
            AuthorizationContext(sessionRef = "owner-session")
        )

    private fun contributor(fixture: SubmissionRuntimeSqlFixture) =
        RequestAccessContext(
            PrincipalRef.user(fixture.contributorUserId),
            AuthorizationContext(sessionRef = "contributor-session")
        )

    private fun attestor(fixture: SubmissionRuntimeSqlFixture) =
        RequestAccessContext(
            PrincipalRef.user(fixture.attestorUserId),
            AuthorizationContext(sessionRef = "attestor-session")
        )
}
