package com.docuhyphen.app.api.service.informationrequest.record

import com.docuhyphen.app.api.migration.SubmissionRuntimeSqlFixture
import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.migration.queryInt
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.audit.CreateInformationRequestRecordExportCommand
import com.docuhyphen.app.api.model.informationrequest.noauth.InformationRequestAbuseLimits
import com.docuhyphen.app.api.model.recordpreservation.RecordTransferVerdict
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.record.InformationRequestRecordExportRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateDefinitionRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionCapabilityRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionRepository
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.model.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestRuntimeTestServices
import com.docuhyphen.app.api.service.informationrequest.audit.InformationRequestAuditActorLabelResolver
import com.docuhyphen.app.api.service.informationrequest.audit.InformationRequestReadAudit
import com.docuhyphen.app.api.service.informationrequest.capability.InformationRequestTemplateWalkingSkeletonStore
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.noauth.InformationRequestRateLimitedException
import com.docuhyphen.app.api.service.informationrequest.oversight.InformationRequestOperationsPostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.record.model.RecordExportTarget
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateConfigurationWriter
import com.docuhyphen.app.api.service.informationrequest.template.singleFieldConfiguration
import com.docuhyphen.app.api.service.recordpreservation.RecordDisposalService
import com.docuhyphen.app.api.service.recordpreservation.RecordStorageLocationPolicy
import com.docuhyphen.app.api.service.recordpreservation.RecordTransferPolicy
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Clock
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(InformationRequestOperationsPostgreSQLResource::class)
class InformationRequestRecordExportAllowanceConcurrencyTransactionTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var runtime: InformationRequestRuntimeTestServices

    @Inject
    lateinit var requirementRepository: InformationRequestRequirementRepository

    @Inject
    lateinit var exportRepository: InformationRequestRecordExportRepository

    @Inject
    lateinit var assembler: InformationRequestRecordAssembler

    @Inject
    lateinit var recordDisposals: RecordDisposalService

    @Inject
    lateinit var storageLocations: RecordStorageLocationPolicy

    @Inject
    lateinit var transfers: RecordTransferPolicy

    @Inject
    lateinit var auditRecorder: AuditRecorder

    @Inject
    lateinit var actorLabels: InformationRequestAuditActorLabelResolver

    @Inject
    lateinit var readAudit: InformationRequestReadAudit

    @Inject
    lateinit var definitionRepository: InformationRequestTemplateDefinitionRepository

    @Inject
    lateinit var versionRepository: InformationRequestTemplateVersionRepository

    @Inject
    lateinit var capabilityRepository: InformationRequestTemplateVersionCapabilityRepository

    @Inject
    lateinit var configurationWriter: InformationRequestTemplateConfigurationWriter

    private val race by lazy { ExportAllowanceRace(dataSource, exportRepository) }

    private val templates by lazy {
        InformationRequestTemplateWalkingSkeletonStore(
            dataSource, definitionRepository, versionRepository, capabilityRepository, configurationWriter,
        )
    }

    @Test
    fun `two exports of one organization on separate Exchanges cannot both take its last export place`() {
        val (first, second) = organizationTargets(2)

        val outcome = race.run(
            { repository -> exportsOf(first, repository).create(command(first, "organization-first")) },
            { repository -> exportsOf(second, repository).create(command(second, "organization-second")) },
        )

        assertEquals(1, outcome.made, "Only one of two exports may take the organization's last export place")
        assertEquals(1, requestExportsOf(first.ownerId), "The organization's daily ceiling holds across its Exchanges")
        assertTrue(outcome.first.isSuccess)
        assertLimitReached(outcome.second.exceptionOrNull())
        assertTrue(outcome.secondWaitedBehindFirst, "The second export waits while the first holds the allowance")
    }

    @Test
    fun `two exports of one personal owner on Exchanges started by different people cannot both take the owner's last place`() {
        val (first, second) = personalTargets()

        val outcome = race.run(
            { repository -> exportsOf(first, repository).create(command(first, "personal-first")) },
            { repository -> exportsOf(second, repository).create(command(second, "personal-second")) },
        )

        assertEquals(1, outcome.made, "Only one of two exports may take the personal owner's last export place")
        assertEquals(1, requestExportsOf(first.ownerId), "The personal owner's daily ceiling holds across Exchanges")
        assertTrue(outcome.first.isSuccess)
        assertLimitReached(outcome.second.exceptionOrNull())
        assertTrue(outcome.secondWaitedBehindFirst, "The second export waits while the first holds the allowance")
    }

    @Test
    fun `exports of different owners each take their own last place without waiting for the other`() {
        val first = organizationTargets(1).single()
        val second = organizationTargets(1).single()

        val outcome = race.run(
            { repository -> exportsOf(first, repository).create(command(first, "owner-one")) },
            { repository -> exportsOf(second, repository).create(command(second, "owner-two")) },
        )

        assertEquals(2, outcome.made, "Each owner's export takes its own owner's place")
        assertTrue(
            outcome.secondCountedWhileFirstHeld,
            "Another owner's export does not wait for this owner's allowance"
        )
        assertEquals(1, requestExportsOf(first.ownerId))
        assertEquals(1, requestExportsOf(second.ownerId))
    }

    @Test
    fun `an export that fails while holding the owner's last place leaves it to the export waiting behind it`() {
        val (first, second) = organizationTargets(2)
        val refusing = refusingTransfers()

        val outcome = race.run(
            { repository ->
                exportsOf(first, repository, refusing).create(
                    command(
                        first,
                        "refused-transfer",
                        "elsewhere"
                    )
                )
            },
            { repository -> exportsOf(second, repository).create(command(second, "after-refusal")) },
        )

        val refusal =
            assertInstanceOf(InformationRequestLifecycleException::class.java, outcome.first.exceptionOrNull())
        assertEquals(InformationRequestErrorCatalog.TRANSFER_NOT_PERMITTED, refusal.reasonCode)
        assertTrue(outcome.second.isSuccess, "The waiting export takes the place the refused one did not use")
        assertEquals(1, requestExportsOf(first.ownerId))
        assertTrue(outcome.secondWaitedBehindFirst, "The second export waits while the first holds the allowance")
    }

    @Test
    fun `a refused export takes no place, so the owner's next export on another Exchange is made`() {
        val (first, second) = organizationTargets(2)

        val refusal = assertThrows(InformationRequestLifecycleException::class.java) {
            QuarkusTransaction.requiringNew().call {
                exportsOf(first, transferPolicy = refusingTransfers()).create(command(first, "refused", "elsewhere"))
            }
        }
        val made = QuarkusTransaction.requiringNew().call { exportsOf(second).create(command(second, "made")) }

        assertEquals(InformationRequestErrorCatalog.TRANSFER_NOT_PERMITTED, refusal.reasonCode)
        assertEquals(second.requestId, made.export.informationRequestId)
        assertEquals(1, requestExportsOf(first.ownerId))
    }

    @Test
    fun `an export repeated with its key once the ceiling is reached returns the recorded export and takes no place`() {
        val (first, second) = organizationTargets(2)

        val created = QuarkusTransaction.requiringNew().call { exportsOf(first).create(command(first, "repeated")) }
        val repeated = QuarkusTransaction.requiringNew().call { exportsOf(first).create(command(first, "repeated")) }
        val refusal = assertThrows(InformationRequestRateLimitedException::class.java) {
            QuarkusTransaction.requiringNew().call { exportsOf(second).create(command(second, "next")) }
        }

        assertEquals(created.export.id, repeated.export.id)
        assertLimitReached(refusal)
        assertEquals(1, requestExportsOf(first.ownerId))
    }

    @Test
    fun `an export made more than a day ago no longer takes one of the owner's places`() {
        val (first, second) = organizationTargets(2)
        val earlier = QuarkusTransaction.requiringNew().call { exportsOf(first).create(command(first, "earlier")) }
        dataSource.connection.use { connection ->
            execute(
                connection,
                "ALTER TABLE information_request_record_export DISABLE TRIGGER information_request_record_export_append_only"
            )
            execute(
                connection,
                "UPDATE information_request_record_export SET requested_at = now() - interval '25 hours' WHERE id = ?",
                earlier.export.id
            )
            execute(
                connection,
                "ALTER TABLE information_request_record_export ENABLE TRIGGER information_request_record_export_append_only"
            )
        }

        val later = QuarkusTransaction.requiringNew().call { exportsOf(second).create(command(second, "later")) }

        assertNotEquals(earlier.export.id, later.export.id)
        assertEquals(2, requestExportsOf(first.ownerId))
    }

    private fun assertLimitReached(refusal: Throwable?) {
        val limited = assertInstanceOf(InformationRequestRateLimitedException::class.java, refusal)
        assertEquals(InformationRequestErrorCatalog.EXPORT_LIMIT_REACHED, limited.reasonCode)
        assertTrue(limited.retryAfterSeconds > 0)
    }

    private fun organizationTargets(count: Int): List<RecordExportTarget> {
        val first = dataSource.connection.use { SubmissionRuntimeSqlFixture(it) }
        val organizationId = first.template.organizationId
        val others = List(count - 1) {
            dataSource.connection.use { SubmissionRuntimeSqlFixture(it, sharedOrganizationId = organizationId) }
        }
        return (listOf(first) + others).map { RecordExportTarget(it.requestId, organizationId, it.template.userId) }
    }

    private fun personalTargets(): List<RecordExportTarget> {
        val owner = templates.insertOwner()
        val field = templates.insertTextField("record-note")
        val template = templates.insertDraft(owner, "record-collection")
        templates.publish(
            template,
            singleFieldConfiguration(templates.insertSchemaVersion(owner, listOf(field)), field)
        )
        return listOf(owner, templates.insertOwner()).map { initiatedBy ->
            RecordExportTarget(insertPersonalRequest(owner, template.versionId, initiatedBy), owner, owner)
        }
    }

    private fun insertPersonalRequest(owner: UUID, versionId: UUID, initiatedBy: UUID): UUID {
        val exchangeId = UUID.randomUUID()
        val requestId = UUID.randomUUID()
        dataSource.connection.use { connection ->
            execute(
                connection,
                """
                INSERT INTO exchange
                    (id, owner_user_id, initiator_id, is_deleted, require_recipient_sign_in, created_date, last_activity,
                     description, initial_share_message, name, status)
                VALUES (?, ?, ?, FALSE, FALSE, now(), now(), 'Collect process records', 'Please respond',
                        'Process collection', 'ACCEPTED_STARTED')
                """.trimIndent(),
                exchangeId,
                owner,
                initiatedBy,
            )
            execute(
                connection,
                """
                INSERT INTO information_request
                    (id, exchange_id, template_version_id, owner_type, owner_user_id, state, gates_exchange_closure,
                     aggregate_revision, party_revision, created_at, updated_at, issued_at)
                VALUES (?, ?, ?, 'USER', ?, 'ISSUED', TRUE, 1, 1, now(), now(), now())
                """.trimIndent(),
                requestId,
                exchangeId,
                versionId,
                owner,
            )
        }
        return requestId
    }

    private fun exportsOf(
        target: RecordExportTarget,
        repository: InformationRequestRecordExportRepository = exportRepository,
        transferPolicy: RecordTransferPolicy = transfers,
    ) = InformationRequestRecordExportService(
        runtime.build(target.requestId).gate,
        assembler,
        repository,
        requirementRepository,
        storageLocations,
        transferPolicy,
        recordDisposals,
        runtime.commandReceiptService,
        auditRecorder,
        actorLabels,
        readAudit,
        Clock.systemUTC(),
        InformationRequestAbuseLimits(exportDailyCeiling = 1),
    )

    private fun refusingTransfers(): RecordTransferPolicy {
        val refusing = mock<RecordTransferPolicy>()
        whenever(refusing.decide(any(), any())).thenReturn(RecordTransferVerdict.REFUSED)
        return refusing
    }

    private fun command(target: RecordExportTarget, key: String, region: String? = null) =
        CreateInformationRequestRecordExportCommand(
            target.requestId,
            RequestAccessContext(PrincipalRef.user(target.actorId), AuthorizationContext(sessionRef = "owner-session")),
            region,
            key,
        )

    private fun requestExportsOf(ownerId: UUID): Int =
        dataSource.connection.use { connection ->
            queryInt(
                connection,
                "SELECT count(*) FROM information_request_record_export WHERE owner_id = ? AND export_kind = 'REQUEST_RECORD'",
                ownerId,
            )
        }
}
