package com.docuhyphen.app.api.service.informationrequest.privacy

import com.docuhyphen.app.api.exception.InformationRequestCommandRequestException
import com.docuhyphen.app.api.migration.SubmissionRuntimeSqlFixture
import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.migration.queryInt
import com.docuhyphen.app.api.migration.queryString
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.access.InformationRequestOwnerRef
import com.docuhyphen.app.api.model.informationrequest.audit.CreateInformationRequestRecordExportCommand
import com.docuhyphen.app.api.model.informationrequest.disposal.InformationRequestDisposalOutcome
import com.docuhyphen.app.api.model.informationrequest.privacy.InformationRequestItemCorrectionInput
import com.docuhyphen.app.api.model.informationrequest.privacy.InformationRequestPrivacyRequestView
import com.docuhyphen.app.api.model.informationrequest.privacy.LiftInformationRequestSubjectRestrictionCommand
import com.docuhyphen.app.api.model.informationrequest.privacy.RecordInformationRequestPrivacyRequestCommand
import com.docuhyphen.app.api.model.recordpreservation.PlaceRecordPreservationHoldCommand
import com.docuhyphen.app.api.model.recordpreservation.PublishRecordRetentionScheduleCommand
import com.docuhyphen.app.api.model.recordpreservation.RecordTransferVerdict
import com.docuhyphen.app.api.model.recordpreservation.ReleaseRecordPreservationHoldCommand
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.privacy.InformationRequestPrivacyRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.privacy.InformationRequestSubjectRestrictionRepository
import com.docuhyphen.app.api.repository.informationrequest.record.InformationRequestRecordExportRepository
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.service.audit.catalog.AuditEventType
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.model.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.command.CommandReceiptConflictException
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestMutationGate
import com.docuhyphen.app.api.service.informationrequest.InformationRequestRuntimeTestServices
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestOwnerScopeAccess
import com.docuhyphen.app.api.service.informationrequest.audit.InformationRequestAuditActorLabelResolver
import com.docuhyphen.app.api.service.informationrequest.audit.InformationRequestReadAudit
import com.docuhyphen.app.api.service.informationrequest.disposal.InformationRequestDisposalEligibility
import com.docuhyphen.app.api.service.informationrequest.disposal.InformationRequestDisposalService
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.oversight.InformationRequestOperationsPostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.privacy.model.AttributedPrivacyRecord
import com.docuhyphen.app.api.service.informationrequest.privacy.model.PrivacyFixture
import com.docuhyphen.app.api.service.informationrequest.record.InformationRequestRecordAssembler
import com.docuhyphen.app.api.service.informationrequest.record.InformationRequestRecordExportService
import com.docuhyphen.app.api.service.informationrequest.submission.InformationRequestItemCorrectionService
import com.docuhyphen.app.api.service.recordpreservation.*
import com.docuhyphen.app.api.service.storage.DocumentVersionContentService
import io.quarkus.arc.Arc
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Clock
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(InformationRequestOperationsPostgreSQLResource::class)
class InformationRequestPrivacyTransactionTest
{
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var runtime: InformationRequestRuntimeTestServices

    @Inject
    lateinit var privacyRepository: InformationRequestPrivacyRequestRepository

    @Inject
    lateinit var restrictionRepository: InformationRequestSubjectRestrictionRepository

    @Inject
    lateinit var requestRepository: InformationRequestRepository

    @Inject
    lateinit var gate: InformationRequestMutationGate

    @Inject
    lateinit var requirementRepository: InformationRequestRequirementRepository

    @Inject
    lateinit var exportRepository: InformationRequestRecordExportRepository

    @Inject
    lateinit var assembler: InformationRequestRecordAssembler

    @Inject
    lateinit var storageLocations: RecordStorageLocationPolicy

    @Inject
    lateinit var transfers: RecordTransferPolicy

    @Inject
    lateinit var restrictions: InformationRequestSubjectRestrictionService

    @Inject
    lateinit var corrections: InformationRequestItemCorrectionService

    @Inject
    lateinit var eligibility: InformationRequestDisposalEligibility

    @Inject
    lateinit var disposals: InformationRequestDisposalService

    @Inject
    lateinit var recordDisposals: RecordDisposalService

    @Inject
    lateinit var content: DocumentVersionContentService

    @Inject
    lateinit var preservationAudit: RecordPreservationAudit

    @Inject
    lateinit var auditRecorder: AuditRecorder

    @Inject
    lateinit var actorLabels: InformationRequestAuditActorLabelResolver

    @Inject
    lateinit var readAudit: InformationRequestReadAudit

    @Inject
    lateinit var holds: RecordPreservationHoldService

    @Inject
    lateinit var schedules: RecordRetentionScheduleService

    @Test
    fun `an access request exports the subject's record and a restriction stops fact reuse for that subject`()
    {
        val fixture = subjectFixture()
        val privacy = service(fixture)

        val access = privacy.submit(command(fixture, InformationRequestPrivacyRequestKind.ACCESS))

        assertEquals(InformationRequestPrivacyRequestState.COMPLETED, access.request.state)
        assertEquals(listOf(fixture.runtime.requestId), access.targets.map { it.requestId })
        val exportId = requireNotNull(access.request.recordExportId)
        dataSource.connection.use { connection ->
            assertEquals(
                "SUBJECT_RECORD",
                queryString(
                    connection,
                    "SELECT export_kind FROM information_request_record_export WHERE id = ?",
                    exportId
                )
            )
            assertEquals(
                1,
                queryInt(
                    connection,
                    "SELECT count(*) FROM information_request_record_export_source WHERE export_id = ?",
                    exportId
                )
            )
        }

        val restricted = privacy.submit(command(fixture, InformationRequestPrivacyRequestKind.RESTRICTION))
        assertEquals(InformationRequestPrivacyRequestState.COMPLETED, restricted.request.state)
        assertTrue(
            QuarkusTransaction.requiringNew().call {
                restrictions.isRestricted(
                    InformationRequestOwnerType.ORGANIZATION,
                    fixture.runtime.template.organizationId,
                    fixture.subjectId
                )
            },
        )
        privacy.submit(command(fixture, InformationRequestPrivacyRequestKind.RESTRICTION))
        dataSource.connection.use { connection ->
            assertEquals(
                1,
                queryInt(
                    connection,
                    "SELECT count(*) FROM information_request_subject_restriction WHERE subject_identity_ref_id = ? AND lifted_at IS NULL",
                    fixture.subjectId
                )
            )
        }
    }

    @Test
    fun `a correction is appended against a submitted item, appears in access and export records, and a correction naming no item of the subject is refused without being stranded`()
    {
        val packageId = UUID.randomUUID()
        val itemId = UUID.randomUUID()
        val otherSubjectId = UUID.randomUUID()
        val fixture = subjectFixture { runtime, _ ->
            runtime.insertPackage(packageId, 1)
            runtime.insertItem(
                itemId,
                packageId,
                runtime.documentRequirementId,
                runtime.documentRevisionId,
                runtime.documentBindingId,
                "DOCUMENT"
            )
            runtime.insertSubject(otherSubjectId)
        }
        val privacy = service(fixture)

        val corrected = privacy.submit(
            command(fixture, InformationRequestPrivacyRequestKind.CORRECTION).copy(
                correction = InformationRequestItemCorrectionInput(
                    itemId,
                    JsonPrimitive("Corrected record"),
                    null,
                    "subject.correction"
                ),
            ),
        )

        assertEquals(InformationRequestPrivacyRequestState.COMPLETED, corrected.request.state)
        assertEquals(InformationRequestPrivacyTargetOutcome.CORRECTED, corrected.targets.single().outcome)
        assertEquals(itemId, corrected.correction?.submissionItemId)
        dataSource.connection.use { connection ->
            assertEquals(
                "c".repeat(64),
                queryString(
                    connection,
                    "SELECT item_hash_sha256 FROM information_request_submission_item WHERE id = ?",
                    itemId
                )
            )
            assertEquals(
                1,
                queryInt(
                    connection,
                    "SELECT count(*) FROM audit_outbox WHERE event_type_key = 'information_request.item.correct' AND target_id = ?",
                    fixture.runtime.requestId.toString()
                )
            )
        }
        val correctionId = requireNotNull(corrected.correction).id
        listOf(
            InformationRequestPrivacyRequestKind.ACCESS,
            InformationRequestPrivacyRequestKind.EXPORT
        ).forEach { kind ->
            val exported = privacy.submit(command(fixture, kind))
            assertEquals(InformationRequestPrivacyRequestState.COMPLETED, exported.request.state, kind.name)
            val record =
                exportContent(requireNotNull(exported.request.recordExportId)).getValue("requests").jsonArray.single().jsonObject
            assertCorrection(record, correctionId, itemId, fixture)
        }
        val requestExport = QuarkusTransaction.requiringNew().call {
            exportService(fixture, transfers).create(
                CreateInformationRequestRecordExportCommand(
                    fixture.runtime.requestId,
                    ownerAccess(fixture),
                    null,
                    "export-${UUID.randomUUID()}"
                ),
            )
        }
        assertCorrection(exportContent(requestExport.export.id), correctionId, itemId, fixture)

        val foreignItem = privacy.submit(
            command(fixture, InformationRequestPrivacyRequestKind.CORRECTION).copy(
                correction = InformationRequestItemCorrectionInput(
                    UUID.randomUUID(),
                    JsonPrimitive("x"),
                    null,
                    "subject.correction"
                ),
            ),
        )
        assertEquals(InformationRequestPrivacyRequestState.REFUSED, foreignItem.request.state)
        assertEquals(InformationRequestErrorCatalog.NOT_FOUND, foreignItem.request.refusalCode)
        val otherSubject = privacy.submit(
            RecordInformationRequestPrivacyRequestCommand(
                otherSubjectId, InformationRequestPrivacyRequestKind.CORRECTION, "subject.request", "policy.basis",
                idempotencyKey = "privacy-${UUID.randomUUID()}",
                authorization = AuthorizationContext(),
                correction = InformationRequestItemCorrectionInput(
                    itemId,
                    JsonPrimitive("x"),
                    null,
                    "subject.correction"
                ),
            ),
        )
        assertEquals(InformationRequestPrivacyRequestState.REFUSED, otherSubject.request.state)
        assertEquals(InformationRequestErrorCatalog.NOT_FOUND, otherSubject.request.refusalCode)
        assertEquals(0, recordedCount(fixture.subjectId) + recordedCount(otherSubjectId))
    }

    @Test
    fun `privacy writes, item corrections, exports, and restriction lifts are audited under the owner's account label and the session each command arrived on`() {
        val packageId = UUID.randomUUID()
        val itemId = UUID.randomUUID()
        val fixture = subjectFixture { runtime, _ ->
            runtime.insertPackage(packageId, 1)
            runtime.insertItem(
                itemId,
                packageId,
                runtime.documentRequirementId,
                runtime.documentRevisionId,
                runtime.documentBindingId,
                "DOCUMENT"
            )
        }
        val privacySession = UUID.randomUUID().toString()
        val privacy = service(fixture)

        val corrected = privacy.submit(
            command(fixture, InformationRequestPrivacyRequestKind.CORRECTION).copy(
                authorization = AuthorizationContext(sessionRef = privacySession),
                correction = InformationRequestItemCorrectionInput(
                    itemId,
                    JsonPrimitive("Corrected record"),
                    null,
                    "subject.correction"
                ),
            ),
        )
        val exported = privacy.submit(
            command(fixture, InformationRequestPrivacyRequestKind.EXPORT).copy(
                authorization = AuthorizationContext(
                    sessionRef = privacySession
                )
            ),
        )
        val restrictionId = restrict(fixture)
        QuarkusTransaction.requiringNew().call {
            liftService(fixture).lift(
                LiftInformationRequestSubjectRestrictionCommand(
                    restrictionId,
                    "subject.resolved",
                    "attributed-lift"
                )
            )
        }

        val owner = fixture.runtime.template.userId
        val ownerEmail = dataSource.connection.use { queryString(it, "SELECT email FROM app_user WHERE id = ?", owner) }
        val privacyCommand = AttributedPrivacyRecord("HUMAN", ownerEmail, privacySession, owner)
        assertEquals(
            setOf(privacyCommand),
            attributed("information_request.privacy.record", corrected.request.id.toString())
        )
        assertEquals(
            setOf(privacyCommand),
            attributed("information_request.privacy.complete", corrected.request.id.toString())
        )
        assertEquals(
            setOf(privacyCommand),
            attributed("information_request.item.correct", fixture.runtime.requestId.toString())
        )
        assertEquals(
            setOf(privacyCommand),
            attributed("information_request.request.export", fixture.runtime.requestId.toString())
        )
        assertEquals(
            setOf(privacyCommand),
            attributed("information_request.privacy.complete", exported.request.id.toString())
        )
        val restrictionRequest = dataSource.connection.use {
            queryString(
                it,
                "SELECT privacy_request_id::text FROM information_request_subject_restriction WHERE id = ?",
                restrictionId
            )
        }
        assertEquals(
            setOf(AttributedPrivacyRecord("HUMAN", ownerEmail, "owner-session", owner)),
            attributed("information_request.privacy.restriction_lift", requireNotNull(restrictionRequest)),
        )
    }

    @Test
    fun `a correction without its corrected content is refused before anything is recorded`()
    {
        val fixture = subjectFixture()

        assertThrows(InformationRequestCommandRequestException::class.java) {
            service(fixture).submit(command(fixture, InformationRequestPrivacyRequestKind.CORRECTION))
        }

        assertEquals(0, privacyRequestCount(fixture.subjectId))
    }

    @Test
    fun `an export the transfer policy forbids is recorded as refused rather than left recorded`() {
        val fixture = subjectFixture()
        val refusing = mock<RecordTransferPolicy>()
        whenever(refusing.decide(any(), any())).thenReturn(RecordTransferVerdict.REFUSED)

        val refused = service(fixture, refusing).submit(
            command(fixture, InformationRequestPrivacyRequestKind.EXPORT).copy(transferRegion = "elsewhere")
        )

        assertEquals(InformationRequestPrivacyRequestState.REFUSED, refused.request.state)
        assertEquals(InformationRequestErrorCatalog.TRANSFER_NOT_PERMITTED, refused.request.refusalCode)
        assertEquals(0, recordedCount(fixture.subjectId))
        dataSource.connection.use { connection ->
            assertEquals(
                0,
                queryInt(
                    connection,
                    "SELECT count(*) FROM information_request_record_export WHERE subject_identity_ref_id = ?",
                    fixture.subjectId
                )
            )
        }
    }

    @Test
    fun `a privacy request replays by its idempotency key and a different request under the same key is a conflict`() {
        val fixture = subjectFixture()
        val privacy = service(fixture)
        val first =
            command(fixture, InformationRequestPrivacyRequestKind.ACCESS).copy(idempotencyKey = "subject-access")

        val recorded = privacy.submit(first)
        val replayed = privacy.submit(first)

        assertEquals(recorded.request.id, replayed.request.id)
        assertEquals(InformationRequestPrivacyRequestState.COMPLETED, replayed.request.state)
        assertEquals(recorded.request.recordExportId, replayed.request.recordExportId)
        assertEquals(1, privacyRequestCount(fixture.subjectId))
        dataSource.connection.use { connection ->
            assertEquals(
                1,
                queryInt(
                    connection,
                    "SELECT count(*) FROM information_request_record_export WHERE subject_identity_ref_id = ?",
                    fixture.subjectId
                )
            )
        }
        assertThrows(CommandReceiptConflictException::class.java) {
            privacy.submit(first.copy(requestKind = InformationRequestPrivacyRequestKind.RESTRICTION))
        }
        assertEquals(1, privacyRequestCount(fixture.subjectId))
    }

    @Test
    fun `a deletion request completes and reports a request still open as skipped`() {
        val open = subjectFixture()

        val skipped = service(open).submit(command(open, InformationRequestPrivacyRequestKind.DELETION))

        assertEquals(InformationRequestPrivacyRequestState.COMPLETED, skipped.request.state)
        assertEquals(
            listOf(open.runtime.requestId to InformationRequestPrivacyTargetOutcome.SKIPPED_OPEN),
            skipped.targets.map { it.requestId to it.outcome })
        assertEquals(1, requestCount(open.runtime.requestId))
    }

    @Test
    fun `a deletion request is refused while a hold covers the subject and disposes every finished request about the subject otherwise`() {
        val fixture = subjectFixture()
        finish(fixture.runtime.requestId)
        QuarkusTransaction.requiringNew().run {
            schedules.publish(
                PublishRecordRetentionScheduleCommand(
                    fixture.recordOwner,
                    "INFORMATION_REQUEST",
                    0,
                    null,
                    fixture.principal
                )
            )
            holds.place(
                PlaceRecordPreservationHoldCommand(
                    fixture.recordOwner,
                    "SUBJECT_IDENTITY",
                    fixture.subjectId.toString(),
                    RecordPreservationScope.DESCENDANTS_AND_REFERENCES,
                    "preserve the subject",
                    null,
                    null,
                    fixture.principal,
                ),
            )
        }
        val held = service(fixture).submit(command(fixture, InformationRequestPrivacyRequestKind.DELETION))
        assertEquals(InformationRequestPrivacyRequestState.REFUSED, held.request.state)
        assertEquals(InformationRequestErrorCatalog.RECORD_HELD, held.request.refusalCode)
        assertEquals(InformationRequestPrivacyTargetOutcome.REFUSED, held.targets.single().outcome)

        val unheld = subjectFixture()
        finish(unheld.runtime.requestId)
        val erased = service(unheld).submit(command(unheld, InformationRequestPrivacyRequestKind.DELETION))
        assertEquals(InformationRequestPrivacyRequestState.COMPLETED, erased.request.state)
        assertEquals(InformationRequestPrivacyTargetOutcome.DISPOSAL_CLAIMED, erased.targets.single().outcome)
        dataSource.connection.use { connection ->
            assertEquals(
                0,
                queryInt(connection, "SELECT count(*) FROM information_request WHERE id = ?", unheld.runtime.requestId)
            )
            assertEquals(
                "PRIVACY_DELETION",
                queryString(
                    connection,
                    "SELECT basis FROM record_disposal_tombstone WHERE resource_id = ?",
                    unheld.runtime.requestId
                )
            )
            assertEquals(
                1,
                queryInt(
                    connection,
                    "SELECT count(*) FROM information_request_privacy_request WHERE id = ?",
                    erased.request.id
                )
            )
        }
    }

    @Test
    fun `an access or export about a subject with a record still being disposed is recorded as refused`() {
        val fixture = subjectFixture()
        finish(fixture.runtime.requestId)
        dataSource.connection.use { connection ->
            execute(
                connection,
                "UPDATE information_request SET cancelled_at = now() - INTERVAL '10 days' WHERE id = ?",
                fixture.runtime.requestId,
            )
        }
        QuarkusTransaction.requiringNew().run {
            schedules.publish(
                PublishRecordRetentionScheduleCommand(
                    fixture.recordOwner,
                    "INFORMATION_REQUEST",
                    0,
                    0,
                    fixture.principal
                ),
            )
        }
        val claim =
            disposals.claim(fixture.runtime.requestId, RecordDisposalBasis.RETENTION_SCHEDULE, null, fixture.principal)
        assertTrue(claim is InformationRequestDisposalOutcome.Claimed, claim.toString())

        val outcomes = listOf(InformationRequestPrivacyRequestKind.ACCESS, InformationRequestPrivacyRequestKind.EXPORT)
            .map { kind -> service(fixture).submit(command(fixture, kind)) }

        assertEquals(
            listOf(InformationRequestPrivacyRequestState.REFUSED, InformationRequestPrivacyRequestState.REFUSED),
            outcomes.map { it.request.state },
        )
        assertEquals(
            listOf(
                InformationRequestErrorCatalog.DISPOSAL_IN_PROGRESS,
                InformationRequestErrorCatalog.DISPOSAL_IN_PROGRESS
            ),
            outcomes.map { it.request.refusalCode },
        )
    }

    @Test
    fun `a refused deletion records a disposal denial against each request it could not dispose`() {
        val fixture = subjectFixture()
        finish(fixture.runtime.requestId)
        QuarkusTransaction.requiringNew().run {
            schedules.publish(
                PublishRecordRetentionScheduleCommand(
                    fixture.recordOwner,
                    "INFORMATION_REQUEST",
                    0,
                    null,
                    fixture.principal
                ),
            )
            holds.place(
                PlaceRecordPreservationHoldCommand(
                    fixture.recordOwner,
                    "SUBJECT_IDENTITY",
                    fixture.subjectId.toString(),
                    RecordPreservationScope.DESCENDANTS_AND_REFERENCES,
                    "preserve the subject",
                    null,
                    null,
                    fixture.principal,
                ),
            )
        }

        val held = service(fixture).submit(command(fixture, InformationRequestPrivacyRequestKind.DELETION))

        assertEquals(InformationRequestPrivacyRequestState.REFUSED, held.request.state)
        assertEquals(
            1,
            attributed(AuditEventType.RECORD_DISPOSAL_DENIED.key, fixture.runtime.requestId.toString()).size,
        )
    }

    @Test
    fun `an export whose record went with a disposed request no longer offers its record`() {
        val fixture = subjectFixture()
        finish(fixture.runtime.requestId)
        val privacy = service(fixture)
        val exported = privacy.submit(command(fixture, InformationRequestPrivacyRequestKind.EXPORT))
        assertEquals(InformationRequestPrivacyRequestState.COMPLETED, exported.request.state)
        assertTrue(exported.recordExportAvailable)

        val erased = privacy.submit(command(fixture, InformationRequestPrivacyRequestKind.DELETION))

        assertEquals(InformationRequestPrivacyRequestState.COMPLETED, erased.request.state)
        assertEquals(0, exportCount(requireNotNull(exported.request.recordExportId)))
        assertFalse(privacy.privacyRequest(exported.request.id).recordExportAvailable)
    }

    @Test
    fun `a deletion request disposes the subject's finished requests and skips the ones still open`()
    {
        val openSiblingId = UUID.randomUUID()
        val fixture = subjectFixture { runtime, subjectId -> runtime.insertSiblingRequest(openSiblingId, subjectId) }
        finish(fixture.runtime.requestId)

        val completed = service(fixture).submit(command(fixture, InformationRequestPrivacyRequestKind.DELETION))

        assertEquals(InformationRequestPrivacyRequestState.COMPLETED, completed.request.state)
        assertEquals(
            mapOf(
                fixture.runtime.requestId to InformationRequestPrivacyTargetOutcome.DISPOSAL_CLAIMED,
                openSiblingId to InformationRequestPrivacyTargetOutcome.SKIPPED_OPEN,
            ),
            completed.targets.associate { it.requestId to it.outcome },
        )
        assertEquals(0, requestCount(fixture.runtime.requestId))
        assertEquals(1, requestCount(openSiblingId))
    }

    @Test
    fun `a deletion request disposes none of the subject's finished requests while any one of them may not be disposed`() {
        val heldSiblingId = UUID.randomUUID()
        val fixture = subjectFixture { runtime, subjectId -> runtime.insertSiblingRequest(heldSiblingId, subjectId) }
        finish(fixture.runtime.requestId)
        finish(heldSiblingId)
        QuarkusTransaction.requiringNew().run {
            holds.place(
                PlaceRecordPreservationHoldCommand(
                    fixture.recordOwner,
                    "INFORMATION_REQUEST",
                    heldSiblingId.toString(),
                    RecordPreservationScope.RESOURCE,
                    "preserve one request",
                    null,
                    null,
                    fixture.principal,
                ),
            )
        }

        val refused = service(fixture).submit(command(fixture, InformationRequestPrivacyRequestKind.DELETION))

        assertEquals(InformationRequestPrivacyRequestState.REFUSED, refused.request.state)
        assertEquals(InformationRequestErrorCatalog.RECORD_HELD, refused.request.refusalCode)
        assertEquals(
            listOf(heldSiblingId to InformationRequestPrivacyTargetOutcome.REFUSED),
            refused.targets.map { it.requestId to it.outcome })
        assertEquals(1, requestCount(fixture.runtime.requestId))
        assertEquals(0, claimCount(fixture.runtime.requestId))
    }

    @Test
    fun `a deletion is refused while a request sharing one of its record exports has a held record and later removes that export with the request`() {
        val fixture = subjectFixture()
        val otherSubjectId = UUID.randomUUID()
        val exportId = UUID.randomUUID()
        val sibling = dataSource.connection.use { connection ->
            SubmissionRuntimeSqlFixture(connection, sharedOrganizationId = fixture.runtime.template.organizationId)
                .also { sibling ->
                    sibling.insertSubject(otherSubjectId, UUID.randomUUID())
                    execute(
                        connection,
                        """
                        INSERT INTO information_request_party
                            (id, information_request_id, role_key, subject_identity_ref_id, active, party_revision,
                             assigned_at, created_at, updated_at)
                        VALUES (?, ?, 'SUBJECT', ?, TRUE, 1, now(), now(), now())
                        """.trimIndent(),
                        UUID.randomUUID(),
                        fixture.runtime.requestId,
                        otherSubjectId,
                    )
                    execute(
                        connection,
                        """
                        INSERT INTO information_request_record_export
                            (id, export_kind, subject_identity_ref_id, owner_kind, owner_id, schema_version, content_json,
                             content_hash_algorithm, content_hash, content_length, storage_location, transfer_decision,
                             requested_by_principal_kind, requested_by_principal_id, requested_at)
                        VALUES (?, 'SUBJECT_RECORD', ?, 'ORGANIZATION', ?, 1, '{}', 'SHA_256', ?, 2, 'primary',
                                'NOT_REQUESTED', 'USER', ?, now())
                        """.trimIndent(),
                        exportId,
                        otherSubjectId,
                        fixture.runtime.template.organizationId,
                        "d".repeat(64),
                        fixture.runtime.template.userId,
                    )
                    listOf(fixture.runtime.requestId, sibling.requestId).forEach { requestId ->
                        execute(
                            connection,
                            "INSERT INTO information_request_record_export_source (export_id, information_request_id) VALUES (?, ?)",
                            exportId,
                            requestId,
                        )
                    }
                }
        }
        finish(fixture.runtime.requestId)
        finish(sibling.requestId)
        val hold = QuarkusTransaction.requiringNew().call {
            holds.place(
                PlaceRecordPreservationHoldCommand(
                    fixture.recordOwner,
                    "INFORMATION_REQUEST_EVIDENCE_VERSION",
                    sibling.evidenceVersionId.toString(),
                    RecordPreservationScope.RESOURCE,
                    "preserve one record",
                    null,
                    null,
                    fixture.principal,
                ),
            )
        }

        val refused = service(fixture).submit(command(fixture, InformationRequestPrivacyRequestKind.DELETION))

        assertEquals(InformationRequestPrivacyRequestState.REFUSED, refused.request.state)
        assertEquals(InformationRequestErrorCatalog.RECORD_HELD, refused.request.refusalCode)
        assertEquals(1, requestCount(fixture.runtime.requestId))
        assertEquals(0, claimCount(fixture.runtime.requestId))
        assertEquals(1, exportCount(exportId))

        QuarkusTransaction.requiringNew().run {
            holds.release(
                ReleaseRecordPreservationHoldCommand(
                    hold.hold.id,
                    fixture.recordOwner,
                    "preservation no longer needed",
                    fixture.principal,
                ),
            )
        }
        val erased = service(fixture).submit(command(fixture, InformationRequestPrivacyRequestKind.DELETION))

        assertEquals(InformationRequestPrivacyRequestState.COMPLETED, erased.request.state)
        assertEquals(0, requestCount(fixture.runtime.requestId))
        assertEquals(0, exportCount(exportId))
        assertEquals(1, requestCount(sibling.requestId))
    }

    @Test
    fun `a hold placed after a deletion was assessed refuses the deletion instead of leaving it recorded`() {
        val fixture = subjectFixture()
        finish(fixture.runtime.requestId)
        val racing = mock<InformationRequestDisposalEligibility>()
        whenever(racing.assess(any(), any(), any(), any())).thenAnswer { invocation ->
            val request = invocation.getArgument<InformationRequest>(0)
            val assessment = eligibility.assess(
                request,
                invocation.getArgument<RecordDisposalBasis>(1),
                invocation.getArgument(2),
                invocation.getArgument(3),
            )
            QuarkusTransaction.requiringNew().run {
                holds.place(
                    PlaceRecordPreservationHoldCommand(
                        fixture.recordOwner,
                        "INFORMATION_REQUEST",
                        request.id.toString(),
                        RecordPreservationScope.RESOURCE,
                        "preserve the request",
                        null,
                        null,
                        fixture.principal,
                    ),
                )
            }
            assessment
        }
        val racingDisposals = InformationRequestDisposalService(
            requestRepository, gate, racing, recordDisposals, content, preservationAudit, Clock.systemUTC(),
        )

        val refused = service(fixture, deletions = racingDisposals).submit(
            command(
                fixture,
                InformationRequestPrivacyRequestKind.DELETION
            )
        )

        assertEquals(InformationRequestPrivacyRequestState.REFUSED, refused.request.state)
        assertEquals(InformationRequestErrorCatalog.RECORD_HELD, refused.request.refusalCode)
        assertEquals(
            listOf(fixture.runtime.requestId to InformationRequestPrivacyTargetOutcome.REFUSED),
            refused.targets.map { it.requestId to it.outcome })
        assertEquals(1, requestCount(fixture.runtime.requestId))
        assertEquals(0, claimCount(fixture.runtime.requestId))
        assertEquals(0, recordedCount(fixture.subjectId))
    }

    @Test
    fun `a subject another owner holds is not found`()
    {
        val fixture = subjectFixture()
        val other = subjectFixture()
        val refusal = assertThrows(InformationRequestLifecycleException::class.java) {
            service(fixture).submit(command(other, InformationRequestPrivacyRequestKind.ACCESS))
        }
        assertEquals(InformationRequestErrorCatalog.NOT_FOUND, refusal.reasonCode)
        assertEquals(0, privacyRequestCount(other.subjectId))
    }

    @Test
    fun `lifting a restriction is audited with it and replays by its idempotency key`() {
        val fixture = subjectFixture()
        val restrictionId = restrict(fixture)
        val lifts = liftService(fixture)
        val command = LiftInformationRequestSubjectRestrictionCommand(restrictionId, "subject.resolved", "lift-once")

        val lifted = QuarkusTransaction.requiringNew().call { lifts.lift(command) }
        val replayed = QuarkusTransaction.requiringNew().call { lifts.lift(command) }

        assertEquals(restrictionId, lifted.id)
        assertEquals(restrictionId, replayed.id)
        assertTrue(replayed.liftedAt != null)
        assertEquals("subject.resolved", replayed.liftReasonCode)
        assertEquals(1, liftAuditCount(restrictionId))
        val again = assertThrows(InformationRequestLifecycleException::class.java) {
            QuarkusTransaction.requiringNew().call { lifts.lift(command.copy(idempotencyKey = "lift-again")) }
        }
        assertEquals(InformationRequestErrorCatalog.PRIVACY_REQUEST_STATE_INVALID, again.reasonCode)
        assertThrows(CommandReceiptConflictException::class.java) {
            QuarkusTransaction.requiringNew().call { lifts.lift(command.copy(reasonCode = "subject.other")) }
        }
    }

    @Test
    fun `a lift reason is a short machine code`() {
        val fixture = subjectFixture()
        val restrictionId = restrict(fixture)
        val lifts = liftService(fixture)

        listOf("", "   ", "Resolved by the subject", "a".repeat(129)).forEach { reason ->
            assertThrows(InformationRequestCommandRequestException::class.java) {
                QuarkusTransaction.requiringNew().call {
                    lifts.lift(
                        LiftInformationRequestSubjectRestrictionCommand(
                            restrictionId,
                            reason,
                            "lift-${UUID.randomUUID()}"
                        )
                    )
                }
            }
        }
        dataSource.connection.use { connection ->
            assertEquals(
                1,
                queryInt(
                    connection,
                    "SELECT count(*) FROM information_request_subject_restriction WHERE id = ? AND lifted_at IS NULL",
                    restrictionId
                )
            )
        }
    }

    @Test
    fun `two concurrent lifts of one restriction lift it once and refuse the other as a conflict`() {
        val fixture = subjectFixture()
        val restrictionId = restrict(fixture)
        val lifts = liftService(fixture)

        val outcomes = dataSource.connection.use { blocker ->
            blocker.autoCommit = false
            blocker.prepareStatement("SELECT id FROM information_request_subject_restriction WHERE id = ? FOR UPDATE")
                .use { statement ->
                    statement.setObject(1, restrictionId)
                    statement.executeQuery().close()
                }
            val pending = (1..2).map { attempt ->
                CompletableFuture.supplyAsync {
                    withRequestContext {
                        runCatching {
                            QuarkusTransaction.requiringNew().call {
                                lifts.lift(
                                    LiftInformationRequestSubjectRestrictionCommand(
                                        restrictionId,
                                        "subject.resolved",
                                        "concurrent-$attempt"
                                    )
                                )
                            }
                        }
                    }
                }
            }
            awaitLockWaiters(2)
            blocker.rollback()
            blocker.autoCommit = true
            pending.map { it.get(30, TimeUnit.SECONDS) }
        }

        assertEquals(1, outcomes.count { it.isSuccess })
        val loser = outcomes.single { it.isFailure }.exceptionOrNull()
        assertTrue(loser is InformationRequestLifecycleException, loser.toString())
        assertEquals(
            InformationRequestErrorCatalog.PRIVACY_REQUEST_STATE_INVALID,
            (loser as InformationRequestLifecycleException).reasonCode
        )
        assertEquals(1, liftAuditCount(restrictionId))
    }

    @Test
    fun `concurrent restrictions of one subject with different command keys both complete on one active restriction`() {
        val fixture = subjectFixture()

        val outcomes = concurrentRestrictions(listOf(fixture, fixture), listOf("restrict-first", "restrict-second"))

        assertEquals(2, outcomes.count { it.isSuccess }, outcomes.map { it.exceptionOrNull()?.toString() }.toString())
        assertTrue(outcomes.all { it.getOrThrow().request.state == InformationRequestPrivacyRequestState.COMPLETED })
        assertEquals(2, outcomes.map { it.getOrThrow().request.id }.distinct().size)
        assertEquals(2, privacyRequestCount(fixture.subjectId))
        dataSource.connection.use { connection ->
            assertEquals(
                1,
                queryInt(
                    connection,
                    "SELECT count(*) FROM information_request_subject_restriction WHERE subject_identity_ref_id = ? AND lifted_at IS NULL",
                    fixture.subjectId
                )
            )
        }
    }

    @Test
    fun `concurrent restrictions of one subject with the same command key replay one privacy request`() {
        val fixture = subjectFixture()

        val outcomes = concurrentRestrictions(listOf(fixture, fixture), listOf("restrict-same", "restrict-same"))

        assertEquals(2, outcomes.count { it.isSuccess }, outcomes.map { it.exceptionOrNull()?.toString() }.toString())
        assertEquals(1, outcomes.map { it.getOrThrow().request.id }.distinct().size)
        assertEquals(1, privacyRequestCount(fixture.subjectId))
    }

    @Test
    fun `concurrent restrictions of different subjects both complete`() {
        val first = subjectFixture()
        val second = subjectFixture()

        val outcomes = concurrentRestrictions(listOf(first, second), listOf("restrict-one", "restrict-other"))

        assertEquals(2, outcomes.count { it.isSuccess }, outcomes.map { it.exceptionOrNull()?.toString() }.toString())
        listOf(first, second).forEach { fixture ->
            dataSource.connection.use { connection ->
                assertEquals(
                    1,
                    queryInt(
                        connection,
                        "SELECT count(*) FROM information_request_subject_restriction WHERE subject_identity_ref_id = ? AND lifted_at IS NULL",
                        fixture.subjectId
                    )
                )
            }
        }
    }

    private fun concurrentRestrictions(
        fixtures: List<PrivacyFixture>,
        keys: List<String>,
    ): List<Result<InformationRequestPrivacyRequestView>> =
        dataSource.connection.use { blocker ->
            blocker.autoCommit = false
            blocker.createStatement()
                .use { it.execute("LOCK TABLE information_request_subject_restriction IN SHARE MODE") }
            val pending = fixtures.mapIndexed { index, fixture ->
                CompletableFuture.supplyAsync {
                    withRequestContext {
                        runCatching {
                            service(fixture).submit(
                                command(fixture, InformationRequestPrivacyRequestKind.RESTRICTION)
                                    .copy(idempotencyKey = keys[index])
                            )
                        }
                    }
                }
            }
            awaitLockWaiters(2)
            blocker.rollback()
            blocker.autoCommit = true
            pending.map { it.get(30, TimeUnit.SECONDS) }
        }

    private fun assertCorrection(record: JsonObject, correctionId: UUID, itemId: UUID, fixture: PrivacyFixture) {
        val exported = record.getValue("itemCorrections").jsonArray.single().jsonObject
        assertEquals(correctionId.toString(), exported.getValue("correctionId").jsonPrimitive.content)
        assertEquals(itemId.toString(), exported.getValue("submissionItemId").jsonPrimitive.content)
        assertEquals(
            fixture.runtime.documentRequirementId.toString(),
            exported.getValue("requirementId").jsonPrimitive.content
        )
        assertEquals(JsonPrimitive("Corrected record"), exported.getValue("correctedValue"))
        assertEquals("subject.correction", exported.getValue("reasonCode").jsonPrimitive.content)
    }

    private fun restrict(fixture: PrivacyFixture): UUID {
        service(fixture).submit(command(fixture, InformationRequestPrivacyRequestKind.RESTRICTION))
        return dataSource.connection.use { connection ->
            UUID.fromString(
                queryString(
                    connection,
                    "SELECT id::text FROM information_request_subject_restriction WHERE subject_identity_ref_id = ? AND lifted_at IS NULL",
                    fixture.subjectId
                ),
            )
        }
    }

    private fun liftService(fixture: PrivacyFixture): InformationRequestSubjectRestrictionService =
        InformationRequestSubjectRestrictionService(
            ownerScope(fixture),
            restrictionRepository,
            runtime.commandReceiptService,
            auditRecorder,
            actorLabels,
            Clock.systemUTC()
        )

    private fun liftAuditCount(restrictionId: UUID): Int =
        dataSource.connection.use { connection ->
            queryInt(
                connection,
                "SELECT count(*) FROM audit_outbox WHERE event_type_key = 'information_request.privacy.restriction_lift' AND payload_json LIKE ?",
                "%$restrictionId%",
            )
        }

    private fun <T> withRequestContext(block: () -> T): T {
        val context = Arc.container().requestContext()
        context.activate()
        try {
            return block()
        } finally {
            context.terminate()
        }
    }

    private fun awaitLockWaiters(expected: Int) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
        while (System.nanoTime() < deadline) {
            val waiting = dataSource.connection.use { connection ->
                queryInt(connection, "SELECT COUNT(*) FROM pg_stat_activity WHERE wait_event_type = 'Lock'")
            }
            if (waiting >= expected) return
            Thread.sleep(50)
        }
    }

    private fun exportContent(exportId: UUID): JsonObject =
        dataSource.connection.use { connection ->
            Json.parseToJsonElement(
                requireNotNull(
                    queryString(
                        connection,
                        "SELECT content_json FROM information_request_record_export WHERE id = ?",
                        exportId
                    )
                )
            ).jsonObject
        }

    private fun finish(requestId: UUID) {
        dataSource.connection.use { connection ->
            execute(
                connection,
                "UPDATE information_request SET state = 'CANCELLED', cancelled_at = now() WHERE id = ?",
                requestId
            )
        }
    }

    private fun requestCount(requestId: UUID): Int =
        dataSource.connection.use { connection ->
            queryInt(
                connection,
                "SELECT count(*) FROM information_request WHERE id = ?",
                requestId
            )
        }

    private fun claimCount(requestId: UUID): Int =
        dataSource.connection.use { connection ->
            queryInt(
                connection,
                "SELECT count(*) FROM record_disposal_claim WHERE resource_id = ?",
                requestId
            )
        }

    private fun exportCount(exportId: UUID): Int =
        dataSource.connection.use { connection ->
            queryInt(
                connection,
                "SELECT count(*) FROM information_request_record_export WHERE id = ?",
                exportId
            )
        }

    private fun privacyRequestCount(subjectId: UUID): Int =
        dataSource.connection.use { connection ->
            queryInt(
                connection,
                "SELECT count(*) FROM information_request_privacy_request WHERE subject_identity_ref_id = ?",
                subjectId
            )
        }

    private fun recordedCount(subjectId: UUID): Int =
        dataSource.connection.use { connection ->
            queryInt(
                connection,
                "SELECT count(*) FROM information_request_privacy_request WHERE subject_identity_ref_id = ? AND state = 'RECORDED'",
                subjectId
            )
        }

    @Test
    fun `a repeated restriction records its applying restriction and request targets`() {
        val fixture = subjectFixture()
        val privacy = service(fixture)
        val first = privacy.submit(command(fixture, InformationRequestPrivacyRequestKind.RESTRICTION))
        val repeated = privacy.submit(
            command(
                fixture,
                InformationRequestPrivacyRequestKind.RESTRICTION
            ).copy(idempotencyKey = "repeat-restriction")
        )
        assertAll(
            { assertEquals(first.restriction?.id, repeated.restriction?.id) },
            { assertTrue(repeated.targets.any { it.outcome == InformationRequestPrivacyTargetOutcome.RESTRICTED }) },
        )
        dataSource.connection.use { connection ->
            execute(
                connection,
                "UPDATE information_request_subject_restriction SET lifted_at = now(), lifted_by_principal_kind = 'USER', lifted_by_principal_id = ?, lift_reason_code = 'resolved.by.subject' WHERE id = ?",
                fixture.principal.id,
                requireNotNull(first.restriction).id
            )
        }
        assertEquals(first.restriction?.id, privacy.privacyRequest(repeated.request.id).restriction?.id)
    }

    @Test
    fun `privacy disposal claims keep the caller session while finalization is explicitly system attributed`() {
        val fixture = subjectFixture()
        finish(fixture.runtime.requestId)
        val outcome = service(fixture).submit(
            command(fixture, InformationRequestPrivacyRequestKind.DELETION)
                .copy(authorization = AuthorizationContext(sessionRef = "privacy-disposal-session"))
        )
        assertEquals(InformationRequestPrivacyRequestState.COMPLETED, outcome.request.state)
        dataSource.connection.use { connection ->
            assertEquals(
                "privacy-disposal-session", queryString(
                    connection,
                    "SELECT session_id FROM audit_outbox WHERE event_type_key = 'record.disposal.claimed' AND target_id = ?",
                    fixture.runtime.requestId.toString()
                )
            )
            assertEquals(
                "SYSTEM|service-account:00000000-0000-0000-0000-000000000000", queryString(
                    connection,
                    "SELECT actor_kind || '|' || actor_label FROM audit_outbox WHERE event_type_key = 'record.disposal.finalized' AND target_id = ?",
                    fixture.runtime.requestId.toString()
                )
            )
        }
    }

    @Test
    fun `privacy deletion reuses an existing disposal claim and keeps its original basis`() {
        val fixture = subjectFixture()
        finish(fixture.runtime.requestId)
        QuarkusTransaction.requiringNew().run {
            schedules.publish(
                PublishRecordRetentionScheduleCommand(
                    fixture.recordOwner,
                    "INFORMATION_REQUEST",
                    0,
                    0,
                    fixture.principal
                )
            )
        }
        val existing = disposals.claim(
            fixture.runtime.requestId,
            RecordDisposalBasis.RETENTION_SCHEDULE,
            null,
            fixture.principal,
        ) as InformationRequestDisposalOutcome.Claimed
        val outcome = service(fixture).submit(command(fixture, InformationRequestPrivacyRequestKind.DELETION))
        assertEquals(InformationRequestPrivacyRequestState.COMPLETED, outcome.request.state)
        assertEquals(existing.view.claim.id, outcome.targets.single().disposalClaimId)
    }

    private fun subjectFixture(prepare: (SubmissionRuntimeSqlFixture, UUID) -> Unit = { _, _ -> }): PrivacyFixture =
        dataSource.connection.use { connection ->
            val runtimeFixture = SubmissionRuntimeSqlFixture(connection)
            val subjectId = UUID.randomUUID()
            runtimeFixture.insertSubject(subjectId, UUID.randomUUID())
            prepare(runtimeFixture, subjectId)
            PrivacyFixture(runtimeFixture, subjectId)
        }

    private fun ownerScope(fixture: PrivacyFixture): InformationRequestOwnerScopeAccess
    {
        val access = mock<InformationRequestOwnerScopeAccess>()
        val owner = InformationRequestOwnerRef(
            InformationRequestOwnerType.ORGANIZATION,
            fixture.runtime.template.organizationId
        )
        whenever(access.currentOwner()).thenReturn(owner)
        whenever(
            access.requireAccess(
                owner,
                Action.INFORMATION_REQUEST_MANAGE_PRIVACY
            )
        ).thenReturn(RequestAccessContext(fixture.principal, AuthorizationContext(sessionRef = "owner-session")))
        return access
    }

    private fun exportService(fixture: PrivacyFixture, transferPolicy: RecordTransferPolicy) =
        InformationRequestRecordExportService(
            runtime.build(fixture.runtime.requestId).gate,
            assembler,
            exportRepository,
            requirementRepository,
            storageLocations,
            transferPolicy,
            recordDisposals,
            runtime.commandReceiptService,
            auditRecorder,
            actorLabels,
            readAudit,
            Clock.systemUTC(),
        )

    private fun service(
        fixture: PrivacyFixture,
        transferPolicy: RecordTransferPolicy = transfers,
        deletions: InformationRequestDisposalService = disposals,
    ): InformationRequestPrivacyService =
        InformationRequestPrivacyService(
            ownerScope(fixture),
            privacyRepository,
            exportService(fixture, transferPolicy),
            restrictions,
            corrections,
            deletions,
            runtime.commandReceiptService,
            auditRecorder,
            actorLabels,
            Clock.systemUTC(),
        )

    private fun ownerAccess(fixture: PrivacyFixture) =
        RequestAccessContext(fixture.principal, AuthorizationContext(sessionRef = "owner"))

    private fun command(fixture: PrivacyFixture, kind: InformationRequestPrivacyRequestKind) =
        RecordInformationRequestPrivacyRequestCommand(
            fixture.subjectId, kind, "subject.request", "policy.basis",
            idempotencyKey = "privacy-${UUID.randomUUID()}",
            authorization = AuthorizationContext(),
        )

    private fun attributed(eventTypeKey: String, targetId: String): Set<AttributedPrivacyRecord> =
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "SELECT actor_kind, actor_label, session_id, actor_id FROM audit_outbox WHERE event_type_key = ? AND target_id = ?",
            ).use { statement ->
                statement.setString(1, eventTypeKey)
                statement.setString(2, targetId)
                statement.executeQuery().use { rows ->
                    generateSequence {
                        if (rows.next()) AttributedPrivacyRecord(
                            rows.getString(1),
                            rows.getString(2),
                            rows.getString(3),
                            rows.getObject(4, UUID::class.java)
                        )
                        else null
                    }.toSet()
                }
            }
        }
}
