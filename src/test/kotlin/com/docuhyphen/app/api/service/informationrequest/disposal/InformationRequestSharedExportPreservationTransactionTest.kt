package com.docuhyphen.app.api.service.informationrequest.disposal

import com.docuhyphen.app.api.exception.RecordPreservationErrorCatalog
import com.docuhyphen.app.api.exception.RecordPreservationException
import com.docuhyphen.app.api.migration.*
import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.RecordDisposalBasis
import com.docuhyphen.app.api.model.entity.RecordDisposalState
import com.docuhyphen.app.api.model.entity.RecordPreservationScope
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.audit.CreateInformationRequestSubjectExportCommand
import com.docuhyphen.app.api.model.informationrequest.disposal.InformationRequestDisposalOutcome
import com.docuhyphen.app.api.model.recordpreservation.PlaceRecordPreservationHoldCommand
import com.docuhyphen.app.api.model.recordpreservation.PublishRecordRetentionScheduleCommand
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestMutationGate
import com.docuhyphen.app.api.service.informationrequest.disposal.model.SharedExportWithRecords
import com.docuhyphen.app.api.service.informationrequest.disposal.model.SubjectSources
import com.docuhyphen.app.api.service.informationrequest.disposal.model.SubmissionFixtureRef
import com.docuhyphen.app.api.service.informationrequest.oversight.InformationRequestOperationsPostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.record.InformationRequestRecordExportService
import com.docuhyphen.app.api.service.recordpreservation.*
import com.docuhyphen.app.api.service.storage.DocumentVersionContentService
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.sql.Connection
import java.sql.SQLException
import java.time.Clock
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(InformationRequestOperationsPostgreSQLResource::class)
class InformationRequestSharedExportPreservationTransactionTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var schedules: RecordRetentionScheduleService

    @Inject
    lateinit var holds: RecordPreservationHoldService

    @Inject
    lateinit var targets: RecordPreservationTargets

    @Inject
    lateinit var disposals: RecordDisposalService

    @Inject
    lateinit var disposalService: InformationRequestDisposalService

    @Inject
    lateinit var eligibility: InformationRequestDisposalEligibility

    @Inject
    lateinit var requestRepository: InformationRequestRepository

    @Inject
    lateinit var gate: InformationRequestMutationGate

    @Inject
    lateinit var exports: InformationRequestRecordExportService

    @Inject
    lateinit var content: DocumentVersionContentService

    @Inject
    lateinit var preservationAudit: RecordPreservationAudit

    @ParameterizedTest
    @MethodSource("siblingRecordHolds")
    fun `a hold on a record of another source keeps the export they share and refuses the first source's disposal`(
        resourceType: String,
        scope: RecordPreservationScope,
    ) {
        val shared = sharedExportWithRecords()
        place(shared.source, resourceType, shared.siblingRecords.getValue(resourceType), scope)

        assertEquals(InformationRequestErrorCatalog.RECORD_HELD, refusalOf(shared.sibling))
        assertEquals(InformationRequestErrorCatalog.RECORD_HELD, refusalOf(shared.source))
        dataSource.connection.use { connection ->
            assertEquals(1, exportCount(connection, shared.exportId))
            assertEquals(1, requestCount(connection, shared.source.requestId))
            assertEquals(0, claimCount(connection, shared.source.requestId))
        }
    }

    @ParameterizedTest
    @MethodSource("siblingRecordHolds")
    fun `a record of another source cannot be placed on hold while the first source's claim is open, and the export they share goes with that source`(
        resourceType: String,
        scope: RecordPreservationScope,
    ) {
        val shared = sharedExportWithRecords()
        val recordId = shared.siblingRecords.getValue(resourceType)
        val claimId = claim(shared.source)
        dataSource.connection.use { connection ->
            assertEquals(
                1,
                queryInt(
                    connection,
                    "SELECT count(*) FROM record_disposal_claim_scope WHERE claim_id = ? AND resource_type = ? AND resource_id = ? AND direct",
                    claimId,
                    resourceType,
                    recordId.toString(),
                ),
            )
        }

        val refusal = assertThrows(RecordPreservationException::class.java) {
            place(shared.source, resourceType, recordId, scope)
        }
        assertEquals(RecordPreservationErrorCatalog.DISPOSAL_IN_PROGRESS, refusal.reasonCode)
        assertDatabaseRefusesHold(shared.source, resourceType, recordId, scope)

        assertEquals(RecordDisposalState.FINALIZED, disposalService.process(claimId))
        dataSource.connection.use { connection ->
            assertEquals(0, exportCount(connection, shared.exportId))
            assertEquals(0, requestCount(connection, shared.source.requestId))
            assertEquals(1, requestCount(connection, shared.sibling.requestId))
            assertEquals(
                1,
                exportCount(connection, shared.siblingRecords.getValue("INFORMATION_REQUEST_RECORD_EXPORT"))
            )
            assertEquals(0, holdCount(connection, resourceType, recordId))
        }
    }

    @Test
    fun `a record another source gains while the first source's claim is open cannot be placed on hold`() {
        val shared = sharedExportWithRecords()
        val claimId = claim(shared.source)
        val laterExportId = dataSource.connection.use { insertRequestExport(it, shared.sibling) }

        val refusal = assertThrows(RecordPreservationException::class.java) {
            place(shared.source, "INFORMATION_REQUEST_RECORD_EXPORT", laterExportId, RecordPreservationScope.RESOURCE)
        }
        assertEquals(RecordPreservationErrorCatalog.DISPOSAL_IN_PROGRESS, refusal.reasonCode)
        assertDatabaseRefusesHold(
            shared.source,
            "INFORMATION_REQUEST_RECORD_EXPORT",
            laterExportId,
            RecordPreservationScope.RESOURCE
        )

        assertEquals(RecordDisposalState.FINALIZED, disposalService.process(claimId))
        dataSource.connection.use { connection ->
            assertEquals(0, exportCount(connection, shared.exportId))
            assertEquals(1, exportCount(connection, laterExportId))
            assertEquals(0, holdCount(connection, "INFORMATION_REQUEST_RECORD_EXPORT", laterExportId))
        }
    }

    @Test
    fun `a hold on a record another source gains after the first source was assessed refuses that source's claim`() {
        val shared = sharedExportWithRecords()
        val racing = mock<InformationRequestDisposalEligibility>()
        whenever(racing.assess(any(), any(), any(), any())).thenAnswer { invocation ->
            val assessment = eligibility.assess(
                invocation.getArgument<InformationRequest>(0),
                invocation.getArgument<RecordDisposalBasis>(1),
                invocation.getArgument(2),
                invocation.getArgument(3),
            )
            val laterExportId = QuarkusTransaction.requiringNew().call {
                dataSource.connection.use { insertRequestExport(it, shared.sibling) }
            }
            place(shared.source, "INFORMATION_REQUEST_RECORD_EXPORT", laterExportId, RecordPreservationScope.RESOURCE)
            assessment
        }
        val racingDisposals = InformationRequestDisposalService(
            requestRepository, gate, racing, disposals, content, preservationAudit, Clock.systemUTC(),
        )

        val refusal = assertThrows(InformationRequestDisposalRefusedException::class.java) {
            QuarkusTransaction.requiringNew().call {
                racingDisposals.claimFinished(
                    listOf(shared.source.requestId),
                    RecordDisposalBasis.RETENTION_SCHEDULE,
                    null,
                    shared.source.principal,
                )
            }
        }

        assertEquals(
            InformationRequestErrorCatalog.RECORD_HELD,
            refusal.refusals.getValue(shared.source.requestId).reasonCode
        )
        dataSource.connection.use { connection ->
            assertEquals(0, claimCount(connection, shared.source.requestId))
            assertEquals(1, exportCount(connection, shared.exportId))
        }
    }

    @Test
    fun `an export generated with another source's submitted evidence is kept while that evidence is held`() {
        val sources = subjectSourcesWithSubmittedEvidence()
        val export = QuarkusTransaction.requiringNew().call {
            exports.createSubjectExport(
                CreateInformationRequestSubjectExportCommand(
                    sources.source.owner,
                    sources.subjectId,
                    listOf(sources.source.requestId, sources.sibling.requestId),
                    RequestAccessContext(sources.source.principal, AuthorizationContext(mfaSatisfied = true)),
                    null,
                ),
            )
        }
        assertTrue(export.export.contentJson.contains(sources.siblingSubmittedEvidenceVersionId.toString()))
        schedule(sources.source)
        place(
            sources.source,
            "INFORMATION_REQUEST_EVIDENCE_VERSION",
            sources.siblingSubmittedEvidenceVersionId,
            RecordPreservationScope.RESOURCE,
        )

        assertEquals(InformationRequestErrorCatalog.RECORD_HELD, refusalOf(sources.sibling))
        assertEquals(InformationRequestErrorCatalog.RECORD_HELD, refusalOf(sources.source))
        dataSource.connection.use { connection ->
            assertEquals(1, exportCount(connection, export.export.id))
            assertEquals(1, requestCount(connection, sources.source.requestId))
        }
    }

    private fun sharedExportWithRecords(): SharedExportWithRecords =
        dataSource.connection.use { connection ->
            val source = SubmissionRuntimeSqlFixture(connection)
            val sibling = SubmissionRuntimeSqlFixture(connection, sharedOrganizationId = source.template.organizationId)
            val subjectId = UUID.randomUUID()
            source.insertSubject(subjectId, UUID.randomUUID())
            insertSubjectParty(connection, sibling.requestId, subjectId)
            val exportId =
                insertSubjectExport(connection, source, subjectId, listOf(source.requestId, sibling.requestId))
            val packageId = UUID.randomUUID()
            sibling.insertPackage(packageId, 1)
            val notices = NoticeSqlFixture(connection, ClockSqlFixture(connection, sibling))
            val intentId = UUID.randomUUID()
            notices.insertClockIntent(intentId, "RESPONSE_REMINDER", notices.clockEventId)
            notices.claim(intentId)
            val noticeId = UUID.randomUUID()
            notices.insertNotice(noticeId, intentId)
            val siblingExportId = UUID.randomUUID()
            PreservationSqlFixture(connection, sibling).insertExport(siblingExportId)
            listOf(source.requestId, sibling.requestId).forEach { finish(connection, it) }
            SharedExportWithRecords(
                SubmissionFixtureRef.of(source),
                SubmissionFixtureRef.of(sibling),
                mapOf(
                    "INFORMATION_REQUEST_SUBMISSION_PACKAGE" to packageId,
                    "INFORMATION_REQUEST_EVIDENCE_ARTIFACT" to sibling.artifactId,
                    "INFORMATION_REQUEST_EVIDENCE_VERSION" to sibling.evidenceVersionId,
                    "INFORMATION_REQUEST_OUTBOUND_NOTICE" to noticeId,
                    "INFORMATION_REQUEST_RECORD_EXPORT" to siblingExportId,
                    "DOCUMENT" to sibling.documentId,
                    "DOCUMENT_VERSION" to sibling.documentVersionId,
                ),
                exportId,
            )
        }.also { schedule(it.source) }

    private fun subjectSourcesWithSubmittedEvidence(): SubjectSources =
        dataSource.connection.use { connection ->
            val source = SubmissionRuntimeSqlFixture(connection)
            val sibling = SubmissionRuntimeSqlFixture(connection, sharedOrganizationId = source.template.organizationId)
            val packageId = UUID.randomUUID()
            val itemId = UUID.randomUUID()
            sibling.insertPackage(packageId, 1)
            sibling.insertItem(
                itemId,
                packageId,
                sibling.documentRequirementId,
                sibling.documentRevisionId,
                sibling.documentBindingId,
                "DOCUMENT",
                sibling.documentResponseId,
            )
            sibling.insertEvidenceMember(UUID.randomUUID(), packageId, itemId)
            val subjectId = UUID.randomUUID()
            source.insertSubject(subjectId, UUID.randomUUID())
            insertSubjectParty(connection, sibling.requestId, subjectId)
            listOf(source.requestId, sibling.requestId).forEach { finish(connection, it) }
            SubjectSources(
                SubmissionFixtureRef.of(source),
                SubmissionFixtureRef.of(sibling),
                subjectId,
                sibling.evidenceVersionId
            )
        }

    private fun insertSubjectParty(connection: Connection, requestId: UUID, subjectId: UUID) {
        execute(
            connection,
            """
            INSERT INTO information_request_party
                (id, information_request_id, role_key, subject_identity_ref_id, active, party_revision, assigned_at,
                 created_at, updated_at)
            VALUES (?, ?, 'SUBJECT', ?, TRUE, 1, now(), now(), now())
            """.trimIndent(),
            UUID.randomUUID(),
            requestId,
            subjectId,
        )
    }

    private fun insertSubjectExport(
        connection: Connection,
        source: SubmissionRuntimeSqlFixture,
        subjectId: UUID,
        requestIds: List<UUID>,
    ): UUID {
        val exportId = UUID.randomUUID()
        execute(
            connection,
            """
            INSERT INTO information_request_record_export
                (id, export_kind, subject_identity_ref_id, owner_kind, owner_id, schema_version, content_json,
                 content_hash_algorithm, content_hash, content_length, storage_location, transfer_decision,
                 requested_by_principal_kind, requested_by_principal_id, requested_at)
            VALUES (?, 'SUBJECT_RECORD', ?, 'ORGANIZATION', ?, 1, '{}', 'SHA_256', ?, 2, 'primary', 'NOT_REQUESTED',
                    'USER', ?, now())
            """.trimIndent(),
            exportId,
            subjectId,
            source.template.organizationId,
            "d".repeat(64),
            source.template.userId,
        )
        requestIds.forEach { requestId ->
            execute(
                connection,
                "INSERT INTO information_request_record_export_source (export_id, information_request_id) VALUES (?, ?)",
                exportId,
                requestId,
            )
        }
        return exportId
    }

    private fun insertRequestExport(connection: Connection, fixture: SubmissionFixtureRef): UUID {
        val exportId = UUID.randomUUID()
        execute(
            connection,
            """
            INSERT INTO information_request_record_export
                (id, export_kind, information_request_id, owner_kind, owner_id, schema_version, content_json,
                 content_hash_algorithm, content_hash, content_length, storage_location, transfer_decision,
                 requested_by_principal_kind, requested_by_principal_id, requested_at)
            VALUES (?, 'REQUEST_RECORD', ?, 'ORGANIZATION', ?, 1, '{}', 'SHA_256', ?, 2, 'primary', 'NOT_REQUESTED',
                    'USER', ?, now())
            """.trimIndent(),
            exportId,
            fixture.requestId,
            fixture.owner.id,
            "e".repeat(64),
            fixture.principal.id,
        )
        return exportId
    }

    private fun finish(connection: Connection, requestId: UUID) {
        execute(
            connection,
            "UPDATE information_request SET state = 'CANCELLED', cancelled_at = now() - INTERVAL '10 days' WHERE id = ?",
            requestId,
        )
    }

    private fun schedule(fixture: SubmissionFixtureRef) {
        QuarkusTransaction.requiringNew().run {
            schedules.publish(
                PublishRecordRetentionScheduleCommand(
                    fixture.owner,
                    "INFORMATION_REQUEST",
                    0,
                    0,
                    fixture.principal
                )
            )
        }
    }

    private fun place(
        fixture: SubmissionFixtureRef,
        resourceType: String,
        recordId: UUID,
        scope: RecordPreservationScope
    ) =
        QuarkusTransaction.requiringNew().call {
            targets.requireHoldable(fixture.owner, resourceType, recordId.toString())
            holds.place(
                PlaceRecordPreservationHoldCommand(
                    fixture.owner, resourceType, recordId.toString(), scope, "preserve one record", null, null,
                    fixture.principal,
                ),
            )
        }

    private fun assertDatabaseRefusesHold(
        fixture: SubmissionFixtureRef,
        resourceType: String,
        recordId: UUID,
        scope: RecordPreservationScope,
    ) {
        val refusal = assertThrows(SQLException::class.java) {
            dataSource.connection.use { connection ->
                execute(
                    connection,
                    """
                    INSERT INTO audit_legal_hold (id, owner_kind, owner_id, resource_type, resource_id, reason, status, scope,
                                                  effective_from, placed_by_principal_kind, placed_by_principal_id, placed_at,
                                                  created_at, updated_at)
                    VALUES (?, 'ORGANIZATION', ?, ?, ?, 'preserve one record', 'ACTIVE', ?, now(), 'USER', ?, now(), now(), now())
                    """.trimIndent(),
                    UUID.randomUUID(),
                    fixture.owner.id,
                    resourceType,
                    recordId.toString(),
                    scope.name,
                    fixture.principal.id,
                )
            }
        }
        assertTrue(
            refusal.message.orEmpty().contains("a record under disposal cannot be placed on hold"),
            refusal.message
        )
    }

    private fun refusalOf(fixture: SubmissionFixtureRef): String? =
        (disposalService.claim(fixture.requestId, RecordDisposalBasis.RETENTION_SCHEDULE, null, fixture.principal)
                as? InformationRequestDisposalOutcome.Refused)?.reasonCode

    private fun claim(fixture: SubmissionFixtureRef): UUID =
        (disposalService.claim(fixture.requestId, RecordDisposalBasis.RETENTION_SCHEDULE, null, fixture.principal)
                as InformationRequestDisposalOutcome.Claimed).view.claim.id

    private fun exportCount(connection: Connection, exportId: UUID): Int =
        queryInt(connection, "SELECT count(*) FROM information_request_record_export WHERE id = ?", exportId)

    private fun requestCount(connection: Connection, requestId: UUID): Int =
        queryInt(connection, "SELECT count(*) FROM information_request WHERE id = ?", requestId)

    private fun claimCount(connection: Connection, requestId: UUID): Int =
        queryInt(connection, "SELECT count(*) FROM record_disposal_claim WHERE resource_id = ?", requestId)

    private fun holdCount(connection: Connection, resourceType: String, recordId: UUID): Int =
        queryInt(
            connection,
            "SELECT count(*) FROM audit_legal_hold WHERE resource_type = ? AND resource_id = ?",
            resourceType,
            recordId.toString(),
        )

    companion object {
        private val SIBLING_RECORD_TYPES = listOf(
            "INFORMATION_REQUEST_SUBMISSION_PACKAGE",
            "INFORMATION_REQUEST_EVIDENCE_ARTIFACT",
            "INFORMATION_REQUEST_EVIDENCE_VERSION",
            "INFORMATION_REQUEST_OUTBOUND_NOTICE",
            "INFORMATION_REQUEST_RECORD_EXPORT",
            "DOCUMENT",
            "DOCUMENT_VERSION",
        )

        @JvmStatic
        fun siblingRecordHolds(): List<Arguments> =
            SIBLING_RECORD_TYPES.flatMap { resourceType ->
                listOf(RecordPreservationScope.RESOURCE, RecordPreservationScope.DESCENDANTS_AND_REFERENCES)
                    .map { scope -> Arguments.of(resourceType, scope) }
            }
    }
}
