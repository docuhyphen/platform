package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.migration.SubmissionRuntimeSqlFixture
import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.migration.queryInt
import com.docuhyphen.app.api.migration.queryString
import com.docuhyphen.app.api.model.entity.InformationRequestOwnerType
import com.docuhyphen.app.api.model.entity.InformationRequestPrivacyRequestKind
import com.docuhyphen.app.api.model.entity.InformationRequestPrivacyRequestState
import com.docuhyphen.app.api.model.entity.InformationRequestPrivacyTargetOutcome
import com.docuhyphen.app.api.model.entity.RecordPreservationScope
import com.docuhyphen.app.api.model.informationrequest.InformationRequestItemCorrectionInput
import com.docuhyphen.app.api.model.informationrequest.InformationRequestOwnerRef
import com.docuhyphen.app.api.model.informationrequest.RecordInformationRequestPrivacyRequestCommand
import com.docuhyphen.app.api.model.recordpreservation.PlaceRecordPreservationHoldCommand
import com.docuhyphen.app.api.model.recordpreservation.PublishRecordRetentionScheduleCommand
import com.docuhyphen.app.api.model.recordpreservation.RecordOwnerRef
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestItemCorrectionRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestPrivacyRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRecordExportRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestSubjectRestrictionRepository
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.recordpreservation.RecordPreservationHoldService
import com.docuhyphen.app.api.service.recordpreservation.RecordRetentionScheduleService
import com.docuhyphen.app.api.service.recordpreservation.RecordStorageLocationPolicy
import com.docuhyphen.app.api.service.recordpreservation.RecordTransferPolicy
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Clock
import java.util.UUID
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(InformationRequestOperationsPostgreSQLResource::class)
class InformationRequestPrivacyTransactionTest
{
    @Inject lateinit var dataSource: DataSource
    @Inject lateinit var runtime: InformationRequestRuntimeTestServices
    @Inject lateinit var privacyRepository: InformationRequestPrivacyRequestRepository
    @Inject lateinit var restrictionRepository: InformationRequestSubjectRestrictionRepository
    @Inject lateinit var correctionRepository: InformationRequestItemCorrectionRepository
    @Inject lateinit var requestRepository: InformationRequestRepository
    @Inject lateinit var exportRepository: InformationRequestRecordExportRepository
    @Inject lateinit var assembler: InformationRequestRecordAssembler
    @Inject lateinit var storageLocations: RecordStorageLocationPolicy
    @Inject lateinit var transfers: RecordTransferPolicy
    @Inject lateinit var restrictions: InformationRequestSubjectRestrictionService
    @Inject lateinit var corrections: InformationRequestItemCorrectionService
    @Inject lateinit var eligibility: InformationRequestDisposalEligibility
    @Inject lateinit var disposals: InformationRequestDisposalService
    @Inject lateinit var auditRecorder: AuditRecorder
    @Inject lateinit var holds: RecordPreservationHoldService
    @Inject lateinit var schedules: RecordRetentionScheduleService

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
            assertEquals("SUBJECT_RECORD", queryString(connection, "SELECT export_kind FROM information_request_record_export WHERE id = ?", exportId))
            assertEquals(1, queryInt(connection, "SELECT count(*) FROM information_request_record_export_source WHERE export_id = ?", exportId))
        }

        val restricted = privacy.submit(command(fixture, InformationRequestPrivacyRequestKind.RESTRICTION))
        assertEquals(InformationRequestPrivacyRequestState.COMPLETED, restricted.request.state)
        assertTrue(
            QuarkusTransaction.requiringNew().call {
                restrictions.isRestricted(InformationRequestOwnerType.ORGANIZATION, fixture.runtime.template.organizationId, fixture.subjectId)
            },
        )
        privacy.submit(command(fixture, InformationRequestPrivacyRequestKind.RESTRICTION))
        dataSource.connection.use { connection ->
            assertEquals(1, queryInt(connection, "SELECT count(*) FROM information_request_subject_restriction WHERE subject_identity_ref_id = ? AND lifted_at IS NULL", fixture.subjectId))
        }
    }

    @Test
    fun `a correction is appended against a submitted item without rewriting its package`()
    {
        val packageId = UUID.randomUUID()
        val itemId = UUID.randomUUID()
        val otherSubjectId = UUID.randomUUID()
        val fixture = subjectFixture { runtime, _ ->
            runtime.insertPackage(packageId, 1)
            runtime.insertItem(itemId, packageId, runtime.documentRequirementId, runtime.documentRevisionId, runtime.documentBindingId, "DOCUMENT")
            runtime.insertSubject(otherSubjectId)
        }
        val privacy = service(fixture)

        val corrected = privacy.submit(
            command(fixture, InformationRequestPrivacyRequestKind.CORRECTION).copy(
                correction = InformationRequestItemCorrectionInput(itemId, JsonPrimitive("Corrected record"), null, "subject.correction"),
            ),
        )

        assertEquals(InformationRequestPrivacyRequestState.COMPLETED, corrected.request.state)
        assertEquals(InformationRequestPrivacyTargetOutcome.CORRECTED, corrected.targets.single().outcome)
        assertEquals(itemId, corrected.correction?.submissionItemId)
        dataSource.connection.use { connection ->
            assertEquals("c".repeat(64), queryString(connection, "SELECT item_hash_sha256 FROM information_request_submission_item WHERE id = ?", itemId))
            assertEquals(1, queryInt(connection, "SELECT count(*) FROM audit_outbox WHERE event_type_key = 'information_request.item.correct' AND target_id = ?", fixture.runtime.requestId.toString()))
        }
        val foreignItem = assertThrows(InformationRequestLifecycleException::class.java) {
            privacy.submit(
                command(fixture, InformationRequestPrivacyRequestKind.CORRECTION).copy(
                    correction = InformationRequestItemCorrectionInput(UUID.randomUUID(), JsonPrimitive("x"), null, "subject.correction"),
                ),
            )
        }
        assertEquals(InformationRequestErrorCatalog.NOT_FOUND, foreignItem.reasonCode)
        val otherSubject = assertThrows(InformationRequestLifecycleException::class.java) {
            privacy.submit(
                RecordInformationRequestPrivacyRequestCommand(
                    otherSubjectId, InformationRequestPrivacyRequestKind.CORRECTION, "subject.request", "policy.basis",
                    correction = InformationRequestItemCorrectionInput(itemId, JsonPrimitive("x"), null, "subject.correction"),
                ),
            )
        }
        assertEquals(InformationRequestErrorCatalog.NOT_FOUND, otherSubject.reasonCode)
    }

    @Test
    fun `a deletion request is refused while a hold or open request forbids it and disposes every finished request about the subject otherwise`()
    {
        val open = subjectFixture()
        val openRefusal = service(open).submit(command(open, InformationRequestPrivacyRequestKind.DELETION))
        assertEquals(InformationRequestPrivacyRequestState.REFUSED, openRefusal.request.state)
        assertEquals(InformationRequestErrorCatalog.RECORD_NOT_FINISHED, openRefusal.request.refusalCode)

        val fixture = subjectFixture()
        dataSource.connection.use { connection ->
            execute(connection, "UPDATE information_request SET state = 'CANCELLED', cancelled_at = now() WHERE id = ?", fixture.runtime.requestId)
        }
        QuarkusTransaction.requiringNew().run {
            schedules.publish(PublishRecordRetentionScheduleCommand(fixture.recordOwner, "INFORMATION_REQUEST", 0, null, fixture.principal))
            holds.place(
                PlaceRecordPreservationHoldCommand(
                    fixture.recordOwner, "SUBJECT_IDENTITY", fixture.subjectId.toString(), RecordPreservationScope.DESCENDANTS_AND_REFERENCES,
                    "preserve the subject", null, null, fixture.principal,
                ),
            )
        }
        val privacy = service(fixture)
        val held = privacy.submit(command(fixture, InformationRequestPrivacyRequestKind.DELETION))
        assertEquals(InformationRequestPrivacyRequestState.REFUSED, held.request.state)
        assertEquals(InformationRequestErrorCatalog.RECORD_HELD, held.request.refusalCode)
        assertEquals(InformationRequestPrivacyTargetOutcome.REFUSED, held.targets.single().outcome)

        val unheld = subjectFixture()
        dataSource.connection.use { connection ->
            execute(connection, "UPDATE information_request SET state = 'CANCELLED', cancelled_at = now() WHERE id = ?", unheld.runtime.requestId)
        }
        val erased = service(unheld).submit(command(unheld, InformationRequestPrivacyRequestKind.DELETION))
        assertEquals(InformationRequestPrivacyRequestState.COMPLETED, erased.request.state)
        assertEquals(InformationRequestPrivacyTargetOutcome.DISPOSAL_CLAIMED, erased.targets.single().outcome)
        dataSource.connection.use { connection ->
            assertEquals(0, queryInt(connection, "SELECT count(*) FROM information_request WHERE id = ?", unheld.runtime.requestId))
            assertEquals("PRIVACY_DELETION", queryString(connection, "SELECT basis FROM record_disposal_tombstone WHERE resource_id = ?", unheld.runtime.requestId))
            assertEquals(1, queryInt(connection, "SELECT count(*) FROM information_request_privacy_request WHERE id = ?", erased.request.id))
        }
    }

    @Test
    fun `a deletion request disposes none of the subject's requests while any one of them may not be disposed`()
    {
        val openSiblingId = UUID.randomUUID()
        val fixture = subjectFixture { runtime, subjectId -> runtime.insertSiblingRequest(openSiblingId, subjectId) }
        dataSource.connection.use { connection ->
            execute(connection, "UPDATE information_request SET state = 'CANCELLED', cancelled_at = now() WHERE id = ?", fixture.runtime.requestId)
        }

        val refused = service(fixture).submit(command(fixture, InformationRequestPrivacyRequestKind.DELETION))

        assertEquals(InformationRequestPrivacyRequestState.REFUSED, refused.request.state)
        assertEquals(InformationRequestErrorCatalog.RECORD_NOT_FINISHED, refused.request.refusalCode)
        assertEquals(listOf(openSiblingId), refused.targets.map { it.requestId })
        dataSource.connection.use { connection ->
            assertEquals(1, queryInt(connection, "SELECT count(*) FROM information_request WHERE id = ?", fixture.runtime.requestId))
            assertEquals(0, queryInt(connection, "SELECT count(*) FROM record_disposal_claim WHERE resource_id = ?", fixture.runtime.requestId))
        }
    }

    @Test
    fun `a subject another owner holds is not found`()
    {
        val fixture = subjectFixture()
        val other = subjectFixture()
        val refusal = assertThrows(InformationRequestLifecycleException::class.java) {
            service(fixture).submit(RecordInformationRequestPrivacyRequestCommand(other.subjectId, InformationRequestPrivacyRequestKind.ACCESS, "subject.request", "policy.access"))
        }
        assertEquals(InformationRequestErrorCatalog.NOT_FOUND, refusal.reasonCode)
    }

    private fun subjectFixture(prepare: (SubmissionRuntimeSqlFixture, UUID) -> Unit = { _, _ -> }): PrivacyFixture =
        dataSource.connection.use { connection ->
            val runtimeFixture = SubmissionRuntimeSqlFixture(connection)
            val subjectId = UUID.randomUUID()
            runtimeFixture.insertSubject(subjectId, UUID.randomUUID())
            prepare(runtimeFixture, subjectId)
            PrivacyFixture(runtimeFixture, subjectId)
        }

    private fun service(fixture: PrivacyFixture): InformationRequestPrivacyService
    {
        val access = mock<InformationRequestOwnerScopeAccess>()
        val owner = InformationRequestOwnerRef(InformationRequestOwnerType.ORGANIZATION, fixture.runtime.template.organizationId)
        whenever(access.currentOwner()).thenReturn(owner)
        whenever(access.requireAccess(owner, Action.INFORMATION_REQUEST_MANAGE_PRIVACY)).thenReturn(fixture.principal)
        val exports = InformationRequestRecordExportService(
            runtime.build(fixture.runtime.requestId).gate, assembler, exportRepository, storageLocations, transfers,
            runtime.commandReceiptService, auditRecorder, requestRepository, Clock.systemUTC(),
        )
        return InformationRequestPrivacyService(
            access, privacyRepository, restrictionRepository, correctionRepository, requestRepository, exports, restrictions,
            corrections, eligibility, disposals, auditRecorder, Clock.systemUTC(),
        )
    }

    private fun command(fixture: PrivacyFixture, kind: InformationRequestPrivacyRequestKind) =
        RecordInformationRequestPrivacyRequestCommand(fixture.subjectId, kind, "subject.request", "policy.basis")

    private data class PrivacyFixture(val runtime: SubmissionRuntimeSqlFixture, val subjectId: UUID)
    {
        val recordOwner: RecordOwnerRef get() = RecordOwnerRef.organization(runtime.template.organizationId)
        val principal: PrincipalRef get() = PrincipalRef.user(runtime.template.userId)
    }
}
