package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.migration.*
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.*
import com.docuhyphen.app.api.model.recordpreservation.PlaceRecordPreservationHoldCommand
import com.docuhyphen.app.api.model.recordpreservation.PublishRecordRetentionScheduleCommand
import com.docuhyphen.app.api.model.recordpreservation.RecordOwnerRef
import com.docuhyphen.app.api.model.recordpreservation.ReleaseRecordPreservationHoldCommand
import com.docuhyphen.app.api.model.workflow.WorkflowRequirementOperand
import com.docuhyphen.app.api.repository.informationrequest.*
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.service.audit.AuditTargetHistoryService
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.command.CommandPrecondition
import com.docuhyphen.app.api.service.identity.PrincipalDisplayService
import com.docuhyphen.app.api.service.notification.DomainEventDeliveryStandingService
import com.docuhyphen.app.api.service.recordpreservation.RecordPreservationHoldService
import com.docuhyphen.app.api.service.recordpreservation.RecordRetentionScheduleService
import com.docuhyphen.app.api.service.recordpreservation.RecordStorageLocationPolicy
import com.docuhyphen.app.api.service.recordpreservation.RecordTransferPolicy
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(InformationRequestOperationsPostgreSQLResource::class)
class InformationRequestTemplateWalkingSkeletonPhase9Test
{
    @Inject lateinit var dataSource: DataSource
    @Inject lateinit var runtime: InformationRequestRuntimeTestServices
    @Inject lateinit var requestRepository: InformationRequestRepository
    @Inject lateinit var reviewRepository: InformationRequestReviewRepository
    @Inject lateinit var transitionRepository: InformationRequestTransitionRepository
    @Inject lateinit var clockRepository: InformationRequestClockRepository
    @Inject lateinit var clockEventRepository: InformationRequestClockEventRepository
    @Inject lateinit var clockPolicies: InformationRequestClockPolicyService
    @Inject lateinit var clockRecorder: InformationRequestClockRecorder
    @Inject lateinit var operands: InformationRequestWorkflowOperandService
    @Inject lateinit var assembler: InformationRequestRecordAssembler
    @Inject lateinit var exportRepository: InformationRequestRecordExportRepository
    @Inject lateinit var storageLocations: RecordStorageLocationPolicy
    @Inject lateinit var transfers: RecordTransferPolicy
    @Inject lateinit var auditRecorder: AuditRecorder
    @Inject lateinit var auditHistory: AuditTargetHistoryService
    @Inject lateinit var holds: RecordPreservationHoldService
    @Inject lateinit var privacyRepository: InformationRequestPrivacyRequestRepository
    @Inject lateinit var restrictionRepository: InformationRequestSubjectRestrictionRepository
    @Inject lateinit var correctionRepository: InformationRequestItemCorrectionRepository
    @Inject lateinit var restrictions: InformationRequestSubjectRestrictionService
    @Inject lateinit var corrections: InformationRequestItemCorrectionService
    @Inject lateinit var eligibility: InformationRequestDisposalEligibility
    @Inject lateinit var disposals: InformationRequestDisposalService
    @Inject lateinit var processor: InformationRequestClockPointProcessor
    @Inject lateinit var worker: InformationRequestNoticeWorker
    @Inject lateinit var noticeStates: InformationRequestNoticeStateReader
    @Inject lateinit var deliveries: DomainEventDeliveryStandingService
    @Inject lateinit var schedules: RecordRetentionScheduleService
    @Inject lateinit var disposalWorker: InformationRequestDisposalWorker
    @Inject
    lateinit var titleReader: InformationRequestTitleReader
    @Inject
    lateinit var partyRepository: InformationRequestPartyRepository
    @Inject
    lateinit var principalDisplayService: PrincipalDisplayService

    private val support by lazy { InformationRequestReviewTestSupport(dataSource, runtime, requestRepository, reviewRepository) }

    @Test
    fun `a basic-shaped request automates from its exact package, keeps its frozen clock, reconciles and exports its record, and honours privacy, holds, and disposal`()
    {
        lateinit var answers: FieldAnswerSqlFixture
        val subjectId = UUID.randomUUID()
        val fixture = support.fixture(
            beforePublish = { connection, runtime ->
                answers = FieldAnswerSqlFixture(connection, runtime.template, reviewed = true, reusePurpose = REUSE_PURPOSE)
            },
            prepare = { runtime ->
                answers.materialize(runtime)
                runtime.insertSubject(subjectId, UUID.randomUUID())
            },
        )
        val clockVersionId = dataSource.connection.use { ClockSqlFixture(it, fixture.runtime).versionId }
        val services = runtime.build(fixture.requestId, denies = support.roleDenials(fixture))
        val owner = InformationRequestOwnerRef(InformationRequestOwnerType.ORGANIZATION, fixture.runtime.template.organizationId)
        val principal = support.owner(fixture).principal

        val started = QuarkusTransaction.requiringNew().call {
            InformationRequestClockService(
                services.gate, clockRepository, clockEventRepository, clockPolicies, clockRecorder, runtime.commandReceiptService,
                Clock.fixed(START, ZoneOffset.UTC),
            ).start(
                StartInformationRequestClockCommand(
                    fixture.requestId, "response", clockVersionId, InformationRequestClockUrgency.STANDARD, START,
                    support.owner(fixture), "start-response",
                ),
            )
        }
        val firstPackage = support.submitWhole(services, fixture)
        val review = support.reviewsOf(fixture).single()
        val assigned = support.assign(services, fixture, review)
        val answerItem = support.item(fixture, firstPackage, answers.requirementId)
        support.finding(services, fixture, review, answerItem, InformationRequestFindingCorrectionScope.RESPONSE)
        support.record(
            services, fixture, assigned,
            support.draft(
                services, fixture, assigned,
                outcomes = mapOf(
                    answerItem to InformationRequestReviewOutcome.CHANGES_REQUIRED,
                    support.item(fixture, firstPackage, fixture.documentRequirementId) to InformationRequestReviewOutcome.SATISFIED,
                ),
            ),
            "return-answer",
        )
        support.patchNarrative(services, fixture, answers.requirementId, "The corrected summary")
        val secondPackage = support.submitWhole(services, fixture)
        val reassigned = support.assign(services, fixture, support.reviewsOf(fixture).last())
        support.record(services, fixture, reassigned, support.draft(services, fixture, reassigned, InformationRequestReviewOutcome.SATISFIED), "accept-answer")
        assertEquals(InformationRequestState.CLOSED, support.state(fixture))

        val exact = QuarkusTransaction.requiringNew().call { operands.frozenValue(fixture.requestId, secondPackage, answers.templateRequirementId, "root") }
        val foreign = QuarkusTransaction.requiringNew().call { operands.frozenValue(UUID.randomUUID(), secondPackage, answers.templateRequirementId, "root") }
        assertTrue(exact is WorkflowRequirementOperand.Value)
        assertTrue(foreign is WorkflowRequirementOperand.Unavailable)
        QuarkusTransaction.requiringNew().run {
            val clock = requireNotNull(clockRepository.findById(started.clock.id))
            assertEquals(clockVersionId, clock.policyVersionId)
            assertEquals(started.clock.dueAt, clock.dueAt)
        }

        val audit = InformationRequestAuditService(services.gate, ownerAccess(owner), auditHistory, transitionRepository)
        val reconciliation = QuarkusTransaction.requiringNew().call { audit.reconciliation(fixture.requestId, support.owner(fixture)) }
        assertTrue(reconciliation.reconciled, "missing=${reconciliation.missing} unmatched=${reconciliation.unmatched}")
        assertTrue(reconciliation.auditedTransitionCount >= 6)
        val reviewHistory = QuarkusTransaction.requiringNew().call {
            audit.events(fixture.requestId, support.owner(fixture), InformationRequestAuditSearch(eventClass = "review"))
        }
        assertTrue(reviewHistory.events.isNotEmpty())

        val exports = exportService(services.gate)
        val exported = QuarkusTransaction.requiringNew().call {
            exports.create(CreateInformationRequestRecordExportCommand(fixture.requestId, support.owner(fixture), null, "export-record"))
        }
        val record = Json.parseToJsonElement(
            requireNotNull(QuarkusTransaction.requiringNew().call { exports.read(fixture.requestId, exported.export.id, support.owner(fixture)) }.content),
        ).jsonObject
        val packages = record.getValue("packages").jsonArray
        assertEquals(2, packages.size)
        assertNotEquals(packages[0].jsonObject.getValue("contentHashSha256"), packages[1].jsonObject.getValue("contentHashSha256"))
        assertEquals(2, record.getValue("reviews").jsonArray.size)
        assertTrue(record.getValue("history").jsonArray.any { it.jsonObject.getValue("mutation").jsonPrimitive.content == "SUBMIT" })

        val privacy = privacyService(owner, services.gate)
        val restricted = privacy.submit(RecordInformationRequestPrivacyRequestCommand(subjectId, InformationRequestPrivacyRequestKind.RESTRICTION, "subject.request", "policy.restriction"))
        assertEquals(InformationRequestPrivacyRequestState.COMPLETED, restricted.request.state)
        val refusedFact = assertThrows(InformationRequestLifecycleException::class.java) {
            QuarkusTransaction.requiringNew().call {
                services.acceptedFacts.promote(
                    PromoteInformationRequestAcceptedFactCommand(
                        requestId = fixture.requestId,
                        packageId = secondPackage,
                        submissionItemId = support.item(fixture, secondPackage, answers.requirementId),
                        purposeKey = REUSE_PURPOSE,
                        policyBasisKey = "policy.reuse",
                        visibility = InformationRequestAcceptedFactVisibility.RESPONDING_PARTIES,
                        access = support.owner(fixture),
                        idempotencyKey = "promote-restricted",
                    ),
                )
            }
        }
        assertEquals(InformationRequestErrorCatalog.SUBJECT_RESTRICTED, refusedFact.reasonCode)
        val access = privacy.submit(RecordInformationRequestPrivacyRequestCommand(subjectId, InformationRequestPrivacyRequestKind.ACCESS, "subject.request", "policy.access"))
        assertEquals(listOf(fixture.requestId), access.targets.map { it.requestId })

        val recordOwner = RecordOwnerRef.organization(owner.ownerId)
        val hold = QuarkusTransaction.requiringNew().call {
            holds.place(
                PlaceRecordPreservationHoldCommand(
                    recordOwner, "EXCHANGE", fixture.runtime.exchangeId.toString(), RecordPreservationScope.DESCENDANTS_AND_REFERENCES,
                    "preserve the Exchange", null, null, principal,
                ),
            )
        }
        val held = privacy.submit(RecordInformationRequestPrivacyRequestCommand(subjectId, InformationRequestPrivacyRequestKind.DELETION, "subject.request", "policy.erasure"))
        assertEquals(InformationRequestErrorCatalog.RECORD_HELD, held.request.refusalCode)

        QuarkusTransaction.requiringNew().run { holds.release(ReleaseRecordPreservationHoldCommand(hold.hold.id, recordOwner, "matter closed", principal)) }
        val erased = privacy.submit(RecordInformationRequestPrivacyRequestCommand(subjectId, InformationRequestPrivacyRequestKind.DELETION, "subject.request", "policy.erasure"))
        assertEquals(InformationRequestPrivacyRequestState.COMPLETED, erased.request.state)
        dataSource.connection.use { connection ->
            assertEquals(0, queryInt(connection, "SELECT count(*) FROM information_request WHERE id = ?", fixture.requestId))
            assertEquals(1, queryInt(connection, "SELECT count(*) FROM record_disposal_tombstone WHERE resource_id = ?", fixture.requestId))
            assertEquals(0, queryInt(connection, "SELECT count(*) FROM information_request_record_export_source WHERE information_request_id = ?", fixture.requestId))
            assertTrue(queryInt(connection, "SELECT count(*) FROM audit_outbox WHERE target_id = ?", fixture.requestId.toString()) > 0)
        }
    }

    @Test
    fun `a staged request is overdue with delivered notices, submits stage by stage, reports its breach, reconciles, exports, and is disposed on schedule`()
    {
        lateinit var staged: SubmissionRuntimeSqlFixture
        val calendarVersion = UUID.randomUUID()
        dataSource.connection.use { connection ->
            staged = SubmissionRuntimeSqlFixture(connection, staged = true)
            ClockSqlFixture(connection, staged).insertVersion(
                calendarVersion, versionNumber = 2, clockType = "CALENDAR", standard = 60, urgent = 60, escalation = null,
            )
        }
        val services = runtime.build(staged.requestId)
        val owner = InformationRequestOwnerRef(InformationRequestOwnerType.ORGANIZATION, staged.template.organizationId)
        val ownerContext = RequestAccessContext(PrincipalRef.user(staged.template.userId), AuthorizationContext(sessionRef = "owner"))
        val clock = QuarkusTransaction.requiringNew().call {
            InformationRequestClockService(
                services.gate, clockRepository, clockEventRepository, clockPolicies, clockRecorder, runtime.commandReceiptService,
                Clock.fixed(START, ZoneOffset.UTC),
            ).start(
                StartInformationRequestClockCommand(
                    staged.requestId, "response", calendarVersion, InformationRequestClockUrgency.STANDARD, START, ownerContext, "start",
                ),
            )
        }.clock
        assertTrue(processor.process(clock.id, START.plusSeconds(3660)))
        assertEquals(2, worker.dispatchForRequest(staged.requestId).delivered)

        stageSubmit(services, staged, "record-stage", attest = false)
        stageSubmit(services, staged, "confirmation-stage", attest = true)
        assertEquals(InformationRequestState.CLOSED, QuarkusTransaction.requiringNew().call { requireNotNull(requestRepository.findById(staged.requestId)).state })
        assertTrue(processor.process(clock.id, Instant.now()))

        val queue = InformationRequestOperationsService(
            ownerAccess(owner), requestRepository, clockRepository, clockEventRepository, clockPolicies, noticeStates, deliveries, Clock.systemUTC(),
            titleReader,
            partyRepository,
            principalDisplayService,
        )
        val row = QuarkusTransaction.requiringNew().call { queue.queue(InformationRequestOperationsFilter()) }.rows.single()
        assertEquals(InformationRequestSlaStatus.OVERDUE, row.standing.status)
        assertEquals(2, row.noticeCounts[InformationRequestNoticeDeliveryState.DELIVERED])

        val audit = InformationRequestAuditService(services.gate, ownerAccess(owner), auditHistory, transitionRepository)
        val reconciliation = QuarkusTransaction.requiringNew().call { audit.reconciliation(staged.requestId, ownerContext) }
        assertTrue(reconciliation.reconciled, "missing=${reconciliation.missing} unmatched=${reconciliation.unmatched}")
        val exports = exportService(services.gate)
        val exported = QuarkusTransaction.requiringNew().call {
            exports.create(CreateInformationRequestRecordExportCommand(staged.requestId, ownerContext, null, "export-staged"))
        }
        val record = Json.parseToJsonElement(
            requireNotNull(QuarkusTransaction.requiringNew().call { exports.read(staged.requestId, exported.export.id, ownerContext) }.content),
        ).jsonObject
        assertEquals(
            listOf("record-stage", "confirmation-stage"),
            record.getValue("packages").jsonArray.map { it.jsonObject.getValue("stageKey").jsonPrimitive.content },
        )
        val notices = record.getValue("notices").jsonArray
        assertEquals(2, notices.size)
        assertTrue(notices.all { it.jsonObject.getValue("maskedEndpoint").jsonPrimitive.content.contains("***@") })

        QuarkusTransaction.requiringNew().run {
            schedules.publish(
                PublishRecordRetentionScheduleCommand(RecordOwnerRef.organization(owner.ownerId), "INFORMATION_REQUEST", 0, 0, ownerContext.principal),
            )
        }
        disposalWorker.run()
        dataSource.connection.use { connection ->
            assertEquals(0, queryInt(connection, "SELECT count(*) FROM information_request WHERE id = ?", staged.requestId))
            assertEquals("RETENTION_SCHEDULE", queryString(connection, "SELECT basis FROM record_disposal_tombstone WHERE resource_id = ?", staged.requestId))
            assertEquals(0, queryInt(connection, "SELECT count(*) FROM information_request_outbound_notice WHERE information_request_id = ?", staged.requestId))
        }
    }

    private fun stageSubmit(services: InformationRequestRuntimeServices, fixture: SubmissionRuntimeSqlFixture, stageKey: String, attest: Boolean)
    {
        val etag = if (attest)
        {
            QuarkusTransaction.requiringNew().call {
                services.attestations.record(
                    RecordInformationRequestSubmissionAttestationCommand(
                        requestId = fixture.requestId,
                        requirementId = fixture.attestationRequirementId,
                        decision = InformationRequestAttestationDecision.ASSENTED,
                        access = RequestAccessContext(PrincipalRef.user(fixture.attestorUserId), AuthorizationContext(sessionRef = "attestor")),
                        precondition = CommandPrecondition.ExpectedRevision(stageETag(fixture, stageKey)),
                        idempotencyKey = "assent-$stageKey",
                    ),
                )
            }.submissionETag
        }
        else stageETag(fixture, stageKey)
        QuarkusTransaction.requiringNew().run {
            services.submissions.submit(
                SubmitInformationRequestPackageCommand(
                    requestId = fixture.requestId,
                    stageKey = stageKey,
                    access = RequestAccessContext(PrincipalRef.user(fixture.contributorUserId), AuthorizationContext(sessionRef = "contributor")),
                    precondition = CommandPrecondition.ExpectedRevision(etag),
                    idempotencyKey = "submit-$stageKey",
                ),
            )
        }
    }

    private fun stageETag(fixture: SubmissionRuntimeSqlFixture, stageKey: String): String =
        QuarkusTransaction.requiringNew().call {
            val request = requireNotNull(requestRepository.findById(fixture.requestId))
            InformationRequestETag.submissionOf(stageKey, runtime.contentCollector.collect(request, stageKey).contentHash)
        }

    private fun ownerAccess(owner: InformationRequestOwnerRef): InformationRequestOwnerScopeAccess
    {
        val access = mock<InformationRequestOwnerScopeAccess>()
        whenever(access.currentOwner()).thenReturn(owner)
        whenever(access.requireAccess(any(), any<Action>())).thenReturn(PrincipalRef.user(UUID.randomUUID()))
        return access
    }

    private fun exportService(gate: InformationRequestMutationGate) = InformationRequestRecordExportService(
        gate, assembler, exportRepository, storageLocations, transfers, runtime.commandReceiptService, auditRecorder, requestRepository, Clock.systemUTC(),
    )

    private fun privacyService(owner: InformationRequestOwnerRef, gate: InformationRequestMutationGate) = InformationRequestPrivacyService(
        ownerAccess(owner), privacyRepository, restrictionRepository, correctionRepository, requestRepository, exportService(gate),
        restrictions, corrections, eligibility, disposals, auditRecorder, Clock.systemUTC(),
    )

    private companion object
    {
        const val REUSE_PURPOSE = "recorded-summary.reuse"
        val START: Instant = Instant.parse("2026-09-25T08:00:00Z")
    }
}
