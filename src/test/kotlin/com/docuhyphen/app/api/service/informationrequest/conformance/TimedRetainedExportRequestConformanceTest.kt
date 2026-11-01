package com.docuhyphen.app.api.service.informationrequest.conformance

import com.docuhyphen.app.api.migration.*
import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.access.InformationRequestOwnerRef
import com.docuhyphen.app.api.model.informationrequest.audit.CreateInformationRequestRecordExportCommand
import com.docuhyphen.app.api.model.informationrequest.audit.InformationRequestAuditSearch
import com.docuhyphen.app.api.model.informationrequest.clock.ChangeInformationRequestClockCommand
import com.docuhyphen.app.api.model.informationrequest.clock.InformationRequestClockChange
import com.docuhyphen.app.api.model.informationrequest.clock.StartInformationRequestClockCommand
import com.docuhyphen.app.api.model.informationrequest.externalsource.*
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestState
import com.docuhyphen.app.api.model.informationrequest.privacy.RecordInformationRequestPrivacyRequestCommand
import com.docuhyphen.app.api.model.informationrequest.submission.RecordInformationRequestSubmissionAttestationCommand
import com.docuhyphen.app.api.model.informationrequest.submission.SubmitInformationRequestPackageCommand
import com.docuhyphen.app.api.model.recordpreservation.PlaceRecordPreservationHoldCommand
import com.docuhyphen.app.api.model.recordpreservation.PublishRecordRetentionScheduleCommand
import com.docuhyphen.app.api.model.recordpreservation.RecordOwnerRef
import com.docuhyphen.app.api.model.recordpreservation.ReleaseRecordPreservationHoldCommand
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.clock.InformationRequestClockEventRepository
import com.docuhyphen.app.api.repository.informationrequest.clock.InformationRequestClockRepository
import com.docuhyphen.app.api.repository.informationrequest.lifecycle.InformationRequestTransitionRepository
import com.docuhyphen.app.api.repository.informationrequest.privacy.InformationRequestItemCorrectionRepository
import com.docuhyphen.app.api.repository.informationrequest.privacy.InformationRequestPrivacyRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.privacy.InformationRequestSubjectRestrictionRepository
import com.docuhyphen.app.api.repository.informationrequest.record.InformationRequestRecordExportRepository
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.service.audit.AuditTargetHistoryService
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.model.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestMutationGate
import com.docuhyphen.app.api.service.informationrequest.InformationRequestRuntimeTestServices
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestOwnerScopeAccess
import com.docuhyphen.app.api.service.informationrequest.audit.InformationRequestAuditActorLabelResolver
import com.docuhyphen.app.api.service.informationrequest.audit.InformationRequestAuditService
import com.docuhyphen.app.api.service.informationrequest.audit.InformationRequestReadAudit
import com.docuhyphen.app.api.service.informationrequest.clock.InformationRequestClockPointProcessor
import com.docuhyphen.app.api.service.informationrequest.clock.InformationRequestClockPolicyService
import com.docuhyphen.app.api.service.informationrequest.clock.InformationRequestClockRecorder
import com.docuhyphen.app.api.service.informationrequest.clock.InformationRequestClockService
import com.docuhyphen.app.api.service.informationrequest.conformance.model.RetainedExternalRecords
import com.docuhyphen.app.api.service.informationrequest.disposal.InformationRequestDisposalEligibility
import com.docuhyphen.app.api.service.informationrequest.disposal.InformationRequestDisposalService
import com.docuhyphen.app.api.service.informationrequest.disposal.InformationRequestDisposalWorker
import com.docuhyphen.app.api.service.informationrequest.model.InformationRequestRuntimeServices
import com.docuhyphen.app.api.service.informationrequest.notice.InformationRequestNoticeWorker
import com.docuhyphen.app.api.service.informationrequest.oversight.InformationRequestOperationsPostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.privacy.InformationRequestPrivacyService
import com.docuhyphen.app.api.service.informationrequest.privacy.InformationRequestSubjectRestrictionService
import com.docuhyphen.app.api.service.informationrequest.record.InformationRequestRecordAssembler
import com.docuhyphen.app.api.service.informationrequest.record.InformationRequestRecordExportService
import com.docuhyphen.app.api.service.informationrequest.submission.InformationRequestItemCorrectionService
import com.docuhyphen.app.api.service.recordpreservation.*
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.security.ForbiddenException
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.security.MessageDigest
import java.time.*
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(InformationRequestOperationsPostgreSQLResource::class)
class TimedRetainedExportRequestConformanceTest
{
    @Inject lateinit var dataSource: DataSource
    @Inject lateinit var runtime: InformationRequestRuntimeTestServices
    @Inject lateinit var requestRepository: InformationRequestRepository
    @Inject lateinit var transitionRepository: InformationRequestTransitionRepository
    @Inject lateinit var clockRepository: InformationRequestClockRepository
    @Inject lateinit var clockEventRepository: InformationRequestClockEventRepository
    @Inject lateinit var clockPolicies: InformationRequestClockPolicyService
    @Inject lateinit var clockRecorder: InformationRequestClockRecorder
    @Inject lateinit var processor: InformationRequestClockPointProcessor
    @Inject lateinit var worker: InformationRequestNoticeWorker
    @Inject lateinit var assembler: InformationRequestRecordAssembler
    @Inject lateinit var exportRepository: InformationRequestRecordExportRepository

    @Inject
    lateinit var requirementRepository: InformationRequestRequirementRepository

    @Inject
    lateinit var readAudit: InformationRequestReadAudit

    @Inject
    lateinit var recordDisposals: RecordDisposalService
    @Inject lateinit var storageLocations: RecordStorageLocationPolicy
    @Inject lateinit var transfers: RecordTransferPolicy
    @Inject lateinit var auditRecorder: AuditRecorder

    @Inject
    lateinit var actorLabels: InformationRequestAuditActorLabelResolver
    @Inject lateinit var auditHistory: AuditTargetHistoryService
    @Inject lateinit var holds: RecordPreservationHoldService
    @Inject lateinit var schedules: RecordRetentionScheduleService
    @Inject lateinit var disposalWorker: InformationRequestDisposalWorker
    @Inject lateinit var privacyRepository: InformationRequestPrivacyRequestRepository
    @Inject lateinit var restrictionRepository: InformationRequestSubjectRestrictionRepository
    @Inject lateinit var correctionRepository: InformationRequestItemCorrectionRepository
    @Inject lateinit var restrictions: InformationRequestSubjectRestrictionService
    @Inject lateinit var corrections: InformationRequestItemCorrectionService
    @Inject lateinit var eligibility: InformationRequestDisposalEligibility
    @Inject lateinit var disposals: InformationRequestDisposalService

    @Inject
    lateinit var authorizationService: AuthorizationService

    @Test
    fun `versioned clock inputs reproduce due times, notices and access history are immutable, held records cannot purge, and the export is reproducible`()
    {
        lateinit var request: SubmissionRuntimeSqlFixture
        val firstVersion = UUID.randomUUID()
        val laterVersion = UUID.randomUUID()
        val subjectId = UUID.randomUUID()
        val reviewerId = UUID.randomUUID()
        dataSource.connection.use { connection ->
            request = SubmissionRuntimeSqlFixture(connection)
            request.insertSubject(subjectId, UUID.randomUUID())
            grantParties(connection, request, reviewerId)
            ClockSqlFixture(connection, request, seed = false).apply {
                insertPolicy(policyId)
                insertVersion(firstVersion, versionNumber = 1, clockType = "CALENDAR", standard = 600, urgent = 60, escalation = null)
                insertReminder(firstVersion, ordinal = 1, minutes = 60)
                insertVersion(laterVersion, versionNumber = 2, clockType = "CALENDAR", standard = 60, urgent = 30, escalation = null)
            }
        }
        val services = runtime.build(request.requestId, centralAuthorization = authorizationService)
        val owner = RequestAccessContext(PrincipalRef.user(request.template.userId), AuthorizationContext(sessionRef = "owner"))
        val reviewer =
            RequestAccessContext(PrincipalRef.user(reviewerId), AuthorizationContext(sessionRef = "reviewer"))
        val time = TestClock(START)
        val clocks = InformationRequestClockService(services.gate, clockRepository, clockEventRepository, clockPolicies, clockRecorder, runtime.commandReceiptService, time)

        val started = QuarkusTransaction.requiringNew().call {
            clocks.start(StartInformationRequestClockCommand(request.requestId, "response", firstVersion, InformationRequestClockUrgency.STANDARD, START, owner, "start-response"))
        }
        assertEquals(START.plusSeconds(10 * HOUR), started.clock.dueAt.toInstant())
        time.instant = START.plusSeconds(2 * HOUR)
        val paused = change(clocks, request, started.clock.id, InformationRequestClockChange.PAUSE, started.clockETag, owner)
        time.instant = START.plusSeconds(5 * HOUR)
        val resumed = change(clocks, request, started.clock.id, InformationRequestClockChange.RESUME, paused.clockETag, owner)
        assertEquals(START.plusSeconds(13 * HOUR), resumed.clock.dueAt.toInstant())
        val extended = change(clocks, request, started.clock.id, InformationRequestClockChange.EXTEND, resumed.clockETag, owner, 120)
        val dueAt = START.plusSeconds(15 * HOUR)
        assertEquals(dueAt, extended.clock.dueAt.toInstant())
        assertEquals(1, extended.clock.dueCycle)
        QuarkusTransaction.requiringNew().run {
            val stored = requireNotNull(clockRepository.findById(started.clock.id))
            assertEquals(firstVersion, stored.policyVersionId)
            assertEquals(dueAt, stored.dueAt.toInstant())
        }

        assertTrue(processor.process(started.clock.id, dueAt.minusSeconds(59 * 60)))
        val kinds = QuarkusTransaction.requiringNew().call { clockEventRepository.findForClock(started.clock.id).map { it.eventKind } }
        assertEquals(
            listOf(
                InformationRequestClockEventKind.STARTED,
                InformationRequestClockEventKind.PAUSED,
                InformationRequestClockEventKind.RESUMED,
                InformationRequestClockEventKind.EXTENDED,
                InformationRequestClockEventKind.REMINDED,
            ),
            kinds,
        )
        val delivered = worker.dispatchForRequest(request.requestId).delivered
        assertTrue(delivered > 0, "the reminder produced delivered notices")
        dataSource.connection.use { connection ->
            val noticeId = requireNotNull(queryString(connection, "SELECT id::text FROM information_request_outbound_notice WHERE information_request_id = ? LIMIT 1", request.requestId))
            refusedBy(connection, "append-only") {
                execute(connection, "DELETE FROM information_request_outbound_notice WHERE id = ?::uuid", noticeId)
            }
            refusedBy(connection, "append-only") {
                execute(connection, "UPDATE information_request_clock_event SET event_kind = 'PAUSED' WHERE clock_id = ?", started.clock.id)
            }
        }

        submitWhole(services, request)
        assertEquals(InformationRequestState.CLOSED, QuarkusTransaction.requiringNew().call { requireNotNull(requestRepository.findById(request.requestId)).state })
        val externalRecords = recordExternalSources(services, request, owner, reviewer)

        val ownerRef = InformationRequestOwnerRef(InformationRequestOwnerType.ORGANIZATION, request.template.organizationId)
        val exports = exportService(services.gate)
        val first = QuarkusTransaction.requiringNew().call { exports.create(CreateInformationRequestRecordExportCommand(request.requestId, owner, null, "export-first")) }
        val second = QuarkusTransaction.requiringNew().call { exports.create(CreateInformationRequestRecordExportCommand(request.requestId, owner, null, "export-second")) }
        val firstContent = requireNotNull(QuarkusTransaction.requiringNew().call { exports.read(request.requestId, first.export.id, owner) }.content)
        assertEquals(first.export.contentHash, sha256(firstContent))
        assertEquals(first.export.contentHash, second.export.contentHash, "an unchanged record exports the same content")
        val exportedSources = Json.parseToJsonElement(firstContent).jsonObject.getValue("externalSources").jsonObject
        assertEquals(
            listOf(externalRecords.valueId.toString()),
            exportedSources.getValue("importedValues").jsonArray.map { it.jsonObject.getValue("importedValueId").jsonPrimitive.content },
        )
        assertEquals(
            listOf(externalRecords.outputId.toString()),
            exportedSources.getValue("generatedOutputs").jsonArray.map { it.jsonObject.getValue("generatedOutputId").jsonPrimitive.content },
        )
        val audit = InformationRequestAuditService(services.gate, ownerAccess(ownerRef), auditHistory, transitionRepository)
        val accessHistory = QuarkusTransaction.requiringNew().call {
            audit.events(request.requestId, owner, InformationRequestAuditSearch(eventTypeKey = "information_request.request.export_read"))
        }
        val exportRead = accessHistory.events.single()
        assertEquals(owner.principal.id, exportRead.record.actorId)
        assertEquals(request.requestId.toString(), exportRead.record.targetId)
        dataSource.connection.use { connection ->
            refusedBy(connection, "append-only") {
                execute(
                    connection,
                    "UPDATE audit_outbox SET event_type_key = 'information_request.request.read' WHERE event_id = ?",
                    exportRead.record.eventId
                )
            }
            refusedBy(connection, "append-only") {
                execute(connection, "DELETE FROM audit_outbox WHERE event_id = ?", exportRead.record.eventId)
            }
            assertEquals(
                1,
                queryInt(connection, "SELECT count(*) FROM audit_outbox WHERE event_id = ?", exportRead.record.eventId)
            )
        }

        val privacy = privacyService(ownerRef, services.gate)
        val access =
            privacy.submit(privacyCommand(subjectId, InformationRequestPrivacyRequestKind.ACCESS, "policy.access"))
        assertEquals(listOf(request.requestId), access.targets.map { it.requestId })

        val recordOwner = RecordOwnerRef.organization(request.template.organizationId)
        QuarkusTransaction.requiringNew().run {
            schedules.publish(PublishRecordRetentionScheduleCommand(recordOwner, "INFORMATION_REQUEST", 0, 0, owner.principal))
        }
        val hold = QuarkusTransaction.requiringNew().call {
            holds.place(
                PlaceRecordPreservationHoldCommand(
                    recordOwner, "EXCHANGE", request.exchangeId.toString(), RecordPreservationScope.DESCENDANTS_AND_REFERENCES,
                    "preserve the Exchange", null, null, owner.principal,
                ),
            )
        }
        disposalWorker.run()
        assertEquals(1, requestCount(request.requestId), "a held record is not purged by its retention schedule")
        val refused =
            privacy.submit(privacyCommand(subjectId, InformationRequestPrivacyRequestKind.DELETION, "policy.erasure"))
        assertEquals(InformationRequestErrorCatalog.RECORD_HELD, refused.request.refusalCode)
        QuarkusTransaction.requiringNew().run { holds.release(ReleaseRecordPreservationHoldCommand(hold.hold.id, recordOwner, "case closed", owner.principal)) }
        disposalWorker.run()
        assertEquals(0, requestCount(request.requestId))
        dataSource.connection.use { connection ->
            assertEquals("RETENTION_SCHEDULE", queryString(connection, "SELECT basis FROM record_disposal_tombstone WHERE resource_id = ?", request.requestId))
            val removed = requireNotNull(queryString(connection, "SELECT removed_rows_json FROM record_disposal_tombstone WHERE resource_id = ?", request.requestId))
            listOf(
                "\"information_request_imported_value\": 1",
                "\"information_request_imported_value_decision\": 1",
                "\"information_request_generated_output\": 1",
            ).forEach { counted -> assertTrue(removed.contains(counted), "$counted in $removed") }
        }
        assertEquals(InformationRequestPrivacyRequestState.REFUSED, refused.request.state)
    }

    private fun recordExternalSources(
        services: InformationRequestRuntimeServices,
        request: SubmissionRuntimeSqlFixture,
        owner: RequestAccessContext,
        reviewer: RequestAccessContext,
    ): RetainedExternalRecords
    {
        val now = Instant.now()
        val value = QuarkusTransaction.requiringNew().call {
            services.importedValueIntake.propose(
                ProposeInformationRequestImportedValueCommand(
                    requestId = request.requestId,
                    requirementId = request.documentRequirementId,
                    resultKey = "issued-on",
                    valueType = FieldValueType.DATE,
                    value = JsonPrimitive("2026-09-20"),
                    sourceReference = "issuing-register",
                    confidence = InformationRequestSourceConfidence.MATCHED,
                    verifiedAt = now.minus(Duration.ofHours(1)),
                    expiresAt = now.plus(Duration.ofDays(365)),
                    provenanceReference = "register-lookup-4",
                    access = owner,
                    idempotencyKey = "record-issue-date",
                ),
            )
        }.value
        val attestor = RequestAccessContext(
            PrincipalRef.user(request.attestorUserId),
            AuthorizationContext(sessionRef = "attestor")
        )
        assertThrows(ForbiddenException::class.java) {
            QuarkusTransaction.requiringNew().run {
                services.importedValueDecisions.decide(
                    DecideInformationRequestImportedValueCommand(
                        request.requestId,
                        value.id,
                        InformationRequestImportedValueDecisionKind.ACCEPTED,
                        "attestor decides",
                        attestor,
                        "attestor-decides",
                    ),
                )
            }
        }
        assertThrows(ForbiddenException::class.java) {
            QuarkusTransaction.requiringNew().call {
                services.importedValueReconciliation.reconcile(
                    ReconcileInformationRequestImportedValuesCommand(
                        request.requestId,
                        attestor,
                        "attestor-reconciles"
                    )
                )
            }
        }
        QuarkusTransaction.requiringNew().run {
            services.importedValueDecisions.decide(
                DecideInformationRequestImportedValueCommand(
                    request.requestId, value.id, InformationRequestImportedValueDecisionKind.ACCEPTED, "register confirms", reviewer, "accept-issue-date",
                ),
            )
        }
        val outcome = QuarkusTransaction.requiringNew().call {
            services.importedValueReconciliation.reconcile(
                ReconcileInformationRequestImportedValuesCommand(
                    request.requestId,
                    reviewer,
                    "reconcile-closed"
                )
            )
        }.single()
        assertEquals(InformationRequestReconciliationOutcome.NOT_COMPARABLE, outcome.outcome)
        val replayed = QuarkusTransaction.requiringNew().call {
            services.importedValueReconciliation.reconcile(
                ReconcileInformationRequestImportedValuesCommand(
                    request.requestId,
                    reviewer,
                    "reconcile-closed"
                )
            )
        }
        assertEquals(listOf(outcome), replayed, "a retried reconciliation returns what the first one reported")
        val packageId = dataSource.connection.use { connection ->
            UUID.fromString(queryString(connection, "SELECT id::text FROM information_request_submission_package WHERE information_request_id = ?", request.requestId))
        }
        val output = QuarkusTransaction.requiringNew().call {
            services.generatedOutputs.record(
                RecordInformationRequestGeneratedOutputCommand(
                    request.requestId, packageId, "closure-summary", "external://summaries/closure-4", "b".repeat(64), "application/pdf",
                    "summary-service", now.minus(Duration.ofMinutes(10)), owner, "record-closure-summary",
                ),
            )
        }
        return RetainedExternalRecords(value.id, output.id)
    }

    private fun submitWhole(services: InformationRequestRuntimeServices, request: SubmissionRuntimeSqlFixture)
    {
        val attested = QuarkusTransaction.requiringNew().call {
            services.attestations.record(
                RecordInformationRequestSubmissionAttestationCommand(
                    requestId = request.requestId,
                    requirementId = request.attestationRequirementId,
                    decision = InformationRequestAttestationDecision.ASSENTED,
                    access = RequestAccessContext(PrincipalRef.user(request.attestorUserId), AuthorizationContext(sessionRef = "attestor")),
                    precondition = CommandPrecondition.ExpectedRevision(
                        QuarkusTransaction.requiringNew().call {
                            val stored = requireNotNull(requestRepository.findById(request.requestId))
                            InformationRequestETag.submissionOf(null, runtime.contentCollector.collect(stored, null).contentHash)
                        },
                    ),
                    idempotencyKey = "assent",
                ),
            )
        }
        QuarkusTransaction.requiringNew().run {
            services.submissions.submit(
                SubmitInformationRequestPackageCommand(
                    requestId = request.requestId,
                    access = RequestAccessContext(PrincipalRef.user(request.contributorUserId), AuthorizationContext(sessionRef = "contributor")),
                    precondition = CommandPrecondition.ExpectedRevision(attested.submissionETag),
                    idempotencyKey = "submit",
                ),
            )
        }
    }

    @Suppress("LongParameterList")
    private fun change(
        clocks: InformationRequestClockService,
        request: SubmissionRuntimeSqlFixture,
        clockId: UUID,
        change: InformationRequestClockChange,
        etag: String,
        owner: RequestAccessContext,
        minutes: Int? = null,
    ) = QuarkusTransaction.requiringNew().call {
        clocks.change(
            ChangeInformationRequestClockCommand(
                requestId = request.requestId,
                clockId = clockId,
                change = change,
                extensionMinutes = minutes,
                reasonCode = "respondent.requested",
                access = owner,
                precondition = CommandPrecondition.ExpectedRevision(etag),
                idempotencyKey = "change-${UUID.randomUUID()}",
            ),
        )
    }

    private fun grantParties(connection: java.sql.Connection, request: SubmissionRuntimeSqlFixture, reviewerId: UUID) {
        val reviewerPartyId = UUID.randomUUID()
        val decisionMakerPartyId = UUID.randomUUID()
        request.insertActingParty(reviewerPartyId, "REVIEWER", reviewerId)
        request.insertPartyForUser(decisionMakerPartyId, "DECISION_MAKER", request.template.userId)
        listOf(
            Triple(request.contributorPartyId, request.contributorUserId, "CONTRIBUTOR"),
            Triple(request.attestorPartyId, request.attestorUserId, "ATTESTOR"),
            Triple(reviewerPartyId, reviewerId, "REVIEWER"),
            Triple(decisionMakerPartyId, request.template.userId, "DECISION_MAKER"),
        ).forEach { (partyId, userId, role) ->
            val shareId = UUID.randomUUID()
            execute(
                connection,
                """
                INSERT INTO share (id, resource_type, resource_id, principal_kind, principal_id, role_name, source, status, granted_at)
                VALUES (?, 'INFORMATION_REQUEST', ?, 'USER', ?, ?, 'DIRECT', 'ACTIVE', now())
                """.trimIndent(),
                shareId,
                request.requestId,
                userId,
                role,
            )
            execute(connection, "UPDATE information_request_party SET share_id = ? WHERE id = ?", shareId, partyId)
        }
        execute(
            connection,
            """
            INSERT INTO share (id, resource_type, resource_id, principal_kind, principal_id, role_name, source, status, granted_at)
            VALUES (?, 'EXCHANGE', ?, 'USER', ?, 'OWNER', 'DIRECT', 'ACTIVE', now())
            """.trimIndent(),
            UUID.randomUUID(),
            request.exchangeId,
            request.template.userId,
        )
    }

    private fun requestCount(requestId: UUID): Int =
        dataSource.connection.use { connection -> queryInt(connection, "SELECT count(*) FROM information_request WHERE id = ?", requestId) }

    private fun sha256(content: String): String =
        MessageDigest.getInstance("SHA-256").digest(content.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun ownerAccess(owner: InformationRequestOwnerRef): InformationRequestOwnerScopeAccess
    {
        val access = mock<InformationRequestOwnerScopeAccess>()
        whenever(access.currentOwner()).thenReturn(owner)
        whenever(access.requireAccess(any(), any<Action>()))
            .thenReturn(
                RequestAccessContext(
                    PrincipalRef.user(UUID.randomUUID()),
                    AuthorizationContext(sessionRef = "owner-session")
                )
            )
        return access
    }

    private fun exportService(gate: InformationRequestMutationGate) = InformationRequestRecordExportService(
        gate, assembler, exportRepository, requirementRepository, storageLocations, transfers, recordDisposals,
        runtime.commandReceiptService, auditRecorder,
        actorLabels, readAudit, Clock.systemUTC(),
    )

    private fun privacyCommand(subjectId: UUID, kind: InformationRequestPrivacyRequestKind, policyBasisKey: String) =
        RecordInformationRequestPrivacyRequestCommand(
            subjectId, kind, "subject.request", policyBasisKey,
            idempotencyKey = "privacy-${UUID.randomUUID()}",
            authorization = AuthorizationContext(),
        )

    private fun privacyService(owner: InformationRequestOwnerRef, gate: InformationRequestMutationGate) = InformationRequestPrivacyService(
        ownerAccess(owner), privacyRepository, exportService(gate), restrictions, corrections, disposals,
        runtime.commandReceiptService, auditRecorder, actorLabels, Clock.systemUTC(),
    )

    private class TestClock(var instant: Instant) : Clock()
    {
        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId): Clock = this

        override fun instant(): Instant = instant
    }

    private companion object
    {
        const val HOUR = 3600L
        val START: Instant = Instant.parse("2026-09-25T08:00:00Z")
    }
}
