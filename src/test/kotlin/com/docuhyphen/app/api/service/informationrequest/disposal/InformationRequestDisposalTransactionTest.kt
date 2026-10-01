package com.docuhyphen.app.api.service.informationrequest.disposal

import com.docuhyphen.app.api.exception.RecordPreservationErrorCatalog
import com.docuhyphen.app.api.exception.RecordPreservationException
import com.docuhyphen.app.api.migration.SubmissionRuntimeSqlFixture
import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.migration.queryInt
import com.docuhyphen.app.api.migration.queryString
import com.docuhyphen.app.api.model.entity.RecordDisposalBasis
import com.docuhyphen.app.api.model.entity.RecordDisposalDeletionOutcome
import com.docuhyphen.app.api.model.entity.RecordDisposalState
import com.docuhyphen.app.api.model.entity.RecordPreservationScope
import com.docuhyphen.app.api.model.informationrequest.disposal.InformationRequestDisposalOutcome
import com.docuhyphen.app.api.model.recordpreservation.PlaceRecordPreservationHoldCommand
import com.docuhyphen.app.api.model.recordpreservation.PublishRecordRetentionScheduleCommand
import com.docuhyphen.app.api.model.recordpreservation.RecordOwnerRef
import com.docuhyphen.app.api.model.recordpreservation.ReleaseRecordPreservationHoldCommand
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.oversight.InformationRequestOperationsPostgreSQLResource
import com.docuhyphen.app.api.service.recordpreservation.RecordDisposalService
import com.docuhyphen.app.api.service.recordpreservation.RecordPreservationHoldService
import com.docuhyphen.app.api.service.recordpreservation.RecordRetentionScheduleService
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.util.UUID
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(InformationRequestOperationsPostgreSQLResource::class)
class InformationRequestDisposalTransactionTest
{
    @Inject lateinit var dataSource: DataSource
    @Inject lateinit var schedules: RecordRetentionScheduleService
    @Inject lateinit var holds: RecordPreservationHoldService
    @Inject lateinit var disposals: RecordDisposalService
    @Inject lateinit var disposalService: InformationRequestDisposalService
    @Inject lateinit var worker: InformationRequestDisposalWorker

    @ConfigProperty(name = "document.version.storage.local.root-directory")
    lateinit var storageRoot: String

    @Test
    fun `a finished request past its disposal age loses its stored bytes and rows and leaves a tombstone and audit trail`()
    {
        val fixture = finishedFixture(daysAgo = 10)
        schedule(fixture, minimumDays = 1, disposalDays = 5)
        val stored = storedFile(fixture)
        assertTrue(stored.isFile)

        val run = worker.run()

        assertTrue(run.finalized >= 1)
        assertFalse(stored.exists())
        dataSource.connection.use { connection ->
            assertEquals(0, queryInt(connection, "SELECT count(*) FROM information_request WHERE id = ?", fixture.requestId))
            assertEquals(0, queryInt(connection, "SELECT count(*) FROM document_version WHERE id = ?", fixture.documentVersionId))
            assertEquals("DELETED", queryString(connection, "SELECT deletion_outcome FROM record_disposal_object WHERE document_version_id = ?", fixture.documentVersionId))
            assertEquals(1, queryInt(connection, "SELECT count(*) FROM record_disposal_tombstone WHERE resource_id = ?", fixture.requestId))
            listOf("record.disposal.claimed", "record.disposal.object_deleted", "record.disposal.finalized").forEach { key ->
                assertEquals(1, queryInt(connection, "SELECT count(*) FROM audit_outbox WHERE event_type_key = ? AND target_id = ?", key, fixture.requestId.toString()), key)
            }
        }
    }

    @Test
    fun `an open request, a request inside its retention, and a request without a disposal age are refused with stable reasons`()
    {
        val open = SubmissionFixtureRef.of(dataSource.connection.use { SubmissionRuntimeSqlFixture(it) })
        schedule(open, minimumDays = 30, disposalDays = null)
        assertRefused(InformationRequestErrorCatalog.RECORD_NOT_FINISHED, open, RecordDisposalBasis.PRIVACY_DELETION)

        val recent = finishedFixture(daysAgo = 1)
        schedule(recent, minimumDays = 30, disposalDays = null)
        assertRefused(InformationRequestErrorCatalog.RETENTION_REQUIRED, recent, RecordDisposalBasis.PRIVACY_DELETION)

        val unscheduled = finishedFixture(daysAgo = 400)
        assertRefused(InformationRequestErrorCatalog.RETENTION_REQUIRED, unscheduled, RecordDisposalBasis.RETENTION_SCHEDULE)
        dataSource.connection.use { connection ->
            assertEquals(1, queryInt(connection, "SELECT count(*) FROM audit_outbox WHERE event_type_key = 'record.disposal.denied' AND target_id = ?", recent.requestId.toString()))
        }
    }

    @Test
    fun `a held request is never claimed and a claimed request cannot be placed on hold`()
    {
        val fixture = finishedFixture(daysAgo = 10)
        schedule(fixture, minimumDays = 0, disposalDays = 0)
        val hold = QuarkusTransaction.requiringNew().call {
            holds.place(
                PlaceRecordPreservationHoldCommand(
                    owner = fixture.owner,
                    resourceType = "EXCHANGE",
                    resourceId = fixture.exchangeId.toString(),
                    scope = RecordPreservationScope.DESCENDANTS_AND_REFERENCES,
                    reason = "preserve the Exchange",
                    caseReference = "matter-1",
                    effectiveFrom = null,
                    principal = fixture.principal,
                ),
            )
        }
        assertRefused(InformationRequestErrorCatalog.RECORD_HELD, fixture, RecordDisposalBasis.RETENTION_SCHEDULE)

        QuarkusTransaction.requiringNew().run {
            holds.release(ReleaseRecordPreservationHoldCommand(hold.hold.id, fixture.owner, "matter closed", fixture.principal))
        }
        val claimed = disposalService.claim(fixture.requestId, RecordDisposalBasis.RETENTION_SCHEDULE, null, fixture.principal)
        assertTrue(claimed is InformationRequestDisposalOutcome.Claimed)

        val refusal = assertThrows(RecordPreservationException::class.java) {
            QuarkusTransaction.requiringNew().call {
                holds.place(
                    PlaceRecordPreservationHoldCommand(
                        fixture.owner, "INFORMATION_REQUEST", fixture.requestId.toString(), RecordPreservationScope.RESOURCE,
                        "preserve the request", null, null, fixture.principal,
                    ),
                )
            }
        }
        assertEquals(RecordPreservationErrorCatalog.DISPOSAL_IN_PROGRESS, refusal.reasonCode)
        dataSource.connection.use { connection ->
            assertEquals(
                "PLACED,RELEASED",
                queryString(connection, "SELECT string_agg(event_kind, ',' ORDER BY event_number) FROM audit_legal_hold_event WHERE hold_id = ?", hold.hold.id),
            )
        }
    }

    @Test
    fun `a failed object deletion stays retryable, a missing object is idempotent success, and a retry after deletion finalizes`()
    {
        val fixture = finishedFixture(daysAgo = 10)
        schedule(fixture, minimumDays = 0, disposalDays = 0)
        val blocking = storedFile(fixture, asDirectory = true)
        val claimId = claimOf(fixture)

        assertEquals(RecordDisposalState.CLAIMED, disposalService.process(claimId))
        val failed = QuarkusTransaction.requiringNew().call { disposals.view(claimId) }
        assertEquals(1, failed.claim.attemptCount)
        assertEquals("DirectoryNotEmptyException", failed.claim.lastErrorCode)

        blocking.deleteRecursively()
        assertEquals(RecordDisposalState.FINALIZED, disposalService.process(claimId))
        dataSource.connection.use { connection ->
            assertEquals("ABSENT", queryString(connection, "SELECT deletion_outcome FROM record_disposal_object WHERE claim_id = ?", claimId))
            assertEquals(0, queryInt(connection, "SELECT count(*) FROM information_request WHERE id = ?", fixture.requestId))
        }

        val interrupted = finishedFixture(daysAgo = 10)
        schedule(interrupted, minimumDays = 0, disposalDays = 0)
        val interruptedClaim = claimOf(interrupted)
        QuarkusTransaction.requiringNew().run {
            disposals.view(interruptedClaim).objects.forEach { disposals.recordObjectDeleted(it.id, RecordDisposalDeletionOutcome.DELETED) }
            disposals.markObjectsDeleted(interruptedClaim)
        }

        assertEquals(RecordDisposalState.FINALIZED, disposalService.process(interruptedClaim))
        assertEquals(RecordDisposalState.FINALIZED, disposalService.process(interruptedClaim))
        dataSource.connection.use { connection ->
            assertEquals(0, queryInt(connection, "SELECT count(*) FROM information_request WHERE id = ?", interrupted.requestId))
            assertEquals(1, queryInt(connection, "SELECT count(*) FROM record_disposal_tombstone WHERE claim_id = ?", interruptedClaim))
        }
    }

    @Test
    fun `shared bytes are retained and a request another live record follows is refused`()
    {
        val shared = finishedFixture(daysAgo = 10)
        schedule(shared, minimumDays = 0, disposalDays = 0)
        dataSource.connection.use { connection ->
            execute(connection, "INSERT INTO exchange_document (exchange_id, documents_id) VALUES (?, ?)", shared.exchangeId, shared.documentId)
        }
        val stored = storedFile(shared)
        val outcome = disposalService.claimAndProcess(shared.requestId, RecordDisposalBasis.RETENTION_SCHEDULE, null, shared.principal)
        assertTrue(outcome is InformationRequestDisposalOutcome.Claimed)
        assertTrue(stored.isFile)
        dataSource.connection.use { connection ->
            assertEquals(1, queryInt(connection, "SELECT count(*) FROM document_version WHERE id = ?", shared.documentVersionId))
            assertEquals(0, queryInt(connection, "SELECT count(*) FROM information_request WHERE id = ?", shared.requestId))
            assertEquals(1, queryInt(connection, "SELECT retained_object_count FROM record_disposal_tombstone WHERE resource_id = ?", shared.requestId))
        }

        val followed = finishedFixture(daysAgo = 10)
        schedule(followed, minimumDays = 0, disposalDays = 0)
        dataSource.connection.use { connection ->
            val successorId = UUID.randomUUID()
            execute(
                connection,
                """
                INSERT INTO information_request
                    (id, exchange_id, template_version_id, owner_type, owner_organization_id, state,
                     gates_exchange_closure, aggregate_revision, party_revision, created_at, updated_at)
                VALUES (?, ?, ?, 'ORGANIZATION', ?, 'DRAFT', TRUE, 1, 1, now(), now())
                """.trimIndent(),
                successorId,
                followed.exchangeId,
                followed.templateVersionId,
                followed.owner.id,
            )
            execute(
                connection,
                """
                INSERT INTO information_request_lineage (id, successor_request_id, source_request_id, lineage_kind,
                                                         created_by_principal_kind, created_by_principal_id)
                VALUES (?, ?, ?, 'SUPPLEMENT', 'USER', ?)
                """.trimIndent(),
                UUID.randomUUID(),
                successorId,
                followed.requestId,
                followed.principal.id,
            )
        }
        assertRefused(InformationRequestErrorCatalog.RECORD_REFERENCED, followed, RecordDisposalBasis.RETENTION_SCHEDULE)
    }

    private fun assertRefused(reasonCode: String, fixture: SubmissionFixtureRef, basis: RecordDisposalBasis)
    {
        val outcome = disposalService.claim(fixture.requestId, basis, if (basis == RecordDisposalBasis.PRIVACY_DELETION) UUID.randomUUID() else null, fixture.principal)
        assertEquals(reasonCode, (outcome as InformationRequestDisposalOutcome.Refused).reasonCode)
    }

    private fun claimOf(fixture: SubmissionFixtureRef): UUID =
        (disposalService.claim(fixture.requestId, RecordDisposalBasis.RETENTION_SCHEDULE, null, fixture.principal) as InformationRequestDisposalOutcome.Claimed)
            .view.claim.id

    private fun finishedFixture(daysAgo: Int): SubmissionFixtureRef =
        dataSource.connection.use { connection ->
            val runtime = SubmissionRuntimeSqlFixture(connection)
            execute(
                connection,
                "UPDATE information_request SET state = 'CANCELLED', cancelled_at = now() - make_interval(days => ?) WHERE id = ?",
                daysAgo,
                runtime.requestId,
            )
            SubmissionFixtureRef.of(runtime)
        }

    private fun schedule(fixture: SubmissionFixtureRef, minimumDays: Int, disposalDays: Int?)
    {
        QuarkusTransaction.requiringNew().run {
            schedules.publish(PublishRecordRetentionScheduleCommand(fixture.owner, "INFORMATION_REQUEST", minimumDays, disposalDays, fixture.principal))
        }
    }

    private fun storedFile(fixture: SubmissionFixtureRef, asDirectory: Boolean = false): File
    {
        val target = File(storageRoot, "document-versions/${fixture.documentVersionId}/process-record.pdf")
        target.parentFile.mkdirs()
        if (asDirectory)
        {
            target.mkdirs()
            File(target, "held-open").writeText("occupied")
        }
        else
        {
            target.writeText("abc")
        }
        return target
    }

    private data class SubmissionFixtureRef(
        val requestId: UUID,
        val exchangeId: UUID,
        val templateVersionId: UUID,
        val documentId: UUID,
        val documentVersionId: UUID,
        val owner: RecordOwnerRef,
        val principal: PrincipalRef,
    )
    {
        companion object
        {
            fun of(runtime: SubmissionRuntimeSqlFixture) = SubmissionFixtureRef(
                requestId = runtime.requestId,
                exchangeId = runtime.exchangeId,
                templateVersionId = runtime.template.versionId,
                documentId = runtime.documentId,
                documentVersionId = runtime.documentVersionId,
                owner = RecordOwnerRef.organization(runtime.template.organizationId),
                principal = PrincipalRef.user(runtime.template.userId),
            )
        }
    }
}
