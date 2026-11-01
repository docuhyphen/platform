package com.docuhyphen.app.api.service.informationrequest.privacy

import com.docuhyphen.app.api.exception.StepUpRequiredException
import com.docuhyphen.app.api.migration.SubmissionRuntimeSqlFixture
import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.migration.queryInt
import com.docuhyphen.app.api.migration.queryString
import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.access.InformationRequestOwnerRef
import com.docuhyphen.app.api.model.informationrequest.audit.CreateInformationRequestRecordExportCommand
import com.docuhyphen.app.api.model.informationrequest.audit.InformationRequestRecordExportView
import com.docuhyphen.app.api.model.informationrequest.lifecycle.CancelInformationRequestCommand
import com.docuhyphen.app.api.model.informationrequest.privacy.InformationRequestPrivacyRequestView
import com.docuhyphen.app.api.model.informationrequest.privacy.RecordInformationRequestPrivacyRequestCommand
import com.docuhyphen.app.api.model.informationrequest.submission.RecordInformationRequestSubmissionAttestationCommand
import com.docuhyphen.app.api.model.informationrequest.submission.SubmitInformationRequestPackageCommand
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.privacy.InformationRequestPrivacyRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.privacy.InformationRequestSubjectRestrictionRepository
import com.docuhyphen.app.api.repository.informationrequest.record.InformationRequestRecordExportRepository
import com.docuhyphen.app.api.repository.informationrequest.submission.InformationRequestSubmissionPackageRepository
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.model.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestRuntimeTestServices
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestOwnerScopeAccess
import com.docuhyphen.app.api.service.informationrequest.audit.InformationRequestAuditActorLabelResolver
import com.docuhyphen.app.api.service.informationrequest.audit.InformationRequestReadAudit
import com.docuhyphen.app.api.service.informationrequest.disposal.InformationRequestDisposalService
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.oversight.InformationRequestOperationsPostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.privacy.model.SubjectExportSnapshotFixture
import com.docuhyphen.app.api.service.informationrequest.record.InformationRequestRecordAssembler
import com.docuhyphen.app.api.service.informationrequest.record.InformationRequestRecordExportService
import com.docuhyphen.app.api.service.informationrequest.submission.InformationRequestItemCorrectionService
import com.docuhyphen.app.api.service.recordpreservation.RecordDisposalService
import com.docuhyphen.app.api.service.recordpreservation.RecordStorageLocationPolicy
import com.docuhyphen.app.api.service.recordpreservation.RecordTransferPolicy
import io.quarkus.arc.Arc
import io.quarkus.arc.ClientProxy
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
import java.sql.Connection
import java.time.Clock
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(InformationRequestOperationsPostgreSQLResource::class)
class InformationRequestSubjectExportSnapshotTransactionTest {
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
    lateinit var restrictions: InformationRequestSubjectRestrictionService

    @Inject
    lateinit var corrections: InformationRequestItemCorrectionService

    @Inject
    lateinit var disposals: InformationRequestDisposalService

    @Inject
    lateinit var auditRecorder: AuditRecorder

    @Inject
    lateinit var actorLabels: InformationRequestAuditActorLabelResolver

    @Inject
    lateinit var readAudit: InformationRequestReadAudit

    @Test
    fun `an access request captured while its source request is cancelled saves the request as it stood before the cancellation`() =
        cancellationDuringCapture(InformationRequestPrivacyRequestKind.ACCESS)

    @Test
    fun `an export request captured while its source request is cancelled saves the request as it stood before the cancellation`() =
        cancellationDuringCapture(InformationRequestPrivacyRequestKind.EXPORT)

    @Test
    fun `a subject export without a competing change saves one coherent request`() {
        val fixture = snapshotFixture()

        val exported = subjectExport(fixture, InformationRequestPrivacyRequestKind.ACCESS, assembler)

        val record = requestRecord(exported, fixture.runtime.requestId)
        assertCoherent(record)
        assertEquals("ISSUED", stateOf(record))
    }

    @Test
    fun `a request export holds its request until its record is saved`() {
        val fixture = snapshotFixture()
        val interleaving = Interleaving(listOf(cancelling(fixture.runtime.requestId, fixture.principal)))

        val exportId = QuarkusTransaction.requiringNew().call {
            exportService(fixture, pausingAssembler(interleaving::duringCapture)).create(
                CreateInformationRequestRecordExportCommand(
                    fixture.runtime.requestId,
                    ownerAccess(fixture),
                    null,
                    "snapshot-export-${UUID.randomUUID()}",
                ),
            ).export.id
        }
        interleaving.awaitCompetitors()

        val record = exportContent(exportId)
        assertCoherent(record)
        assertEquals("ISSUED", stateOf(record))
        assertFalse(interleaving.finishedDuringCapture, "The cancellation waits until the request export is saved")
        assertEquals("CANCELLED", storedState(fixture.runtime.requestId))
    }

    @Test
    fun `a submission made while a subject export is captured waits for it and the export saves the request before that submission`() {
        val fixture = snapshotFixture()
        val services = runtime.build(fixture.runtime.requestId)
        val attested = QuarkusTransaction.requiringNew().call {
            services.attestations.record(
                RecordInformationRequestSubmissionAttestationCommand(
                    requestId = fixture.runtime.requestId,
                    requirementId = fixture.runtime.attestationRequirementId,
                    decision = InformationRequestAttestationDecision.ASSENTED,
                    access = party(fixture.runtime.attestorUserId),
                    precondition = CommandPrecondition.ExpectedRevision(submissionETag(fixture.runtime.requestId)),
                    idempotencyKey = "snapshot-assent-${UUID.randomUUID()}",
                ),
            )
        }
        val stateBeforeSubmission = storedState(fixture.runtime.requestId)
        val submitting: () -> Any? = {
            services.submissions.submit(
                SubmitInformationRequestPackageCommand(
                    requestId = fixture.runtime.requestId,
                    access = party(fixture.runtime.contributorUserId),
                    precondition = CommandPrecondition.ExpectedRevision(attested.submissionETag),
                    idempotencyKey = "snapshot-submit-${UUID.randomUUID()}",
                ),
            )
        }
        val interleaving = Interleaving(listOf(submitting))

        val exported = subjectExport(
            fixture,
            InformationRequestPrivacyRequestKind.ACCESS,
            pausingAssembler(interleaving::duringCapture),
        )
        interleaving.awaitCompetitors()

        val record = requestRecord(exported, fixture.runtime.requestId)
        assertCoherent(record)
        assertEquals(stateBeforeSubmission, stateOf(record))
        assertTrue(record.getValue("packages").jsonArray.isEmpty())
        assertFalse(interleaving.finishedDuringCapture, "The submission waits until the subject export is saved")
        assertEquals(1, packageCount(fixture.runtime.requestId))
    }

    @Test
    fun `a subject export across several Exchanges saves each request before the cancellations that arrive during its capture`() {
        val fixture = snapshotFixture()
        val other = dataSource.connection.use { connection ->
            SubmissionRuntimeSqlFixture(connection, sharedOrganizationId = fixture.runtime.template.organizationId)
                .also { subjectParty(connection, it.requestId, fixture.subjectId) }
        }
        assertNotEquals(fixture.runtime.exchangeId, other.exchangeId)
        val requestIds = listOf(fixture.runtime.requestId, other.requestId)
        val interleaving = Interleaving(requestIds.map { cancelling(it, fixture.principal) })

        val exported = subjectExport(
            fixture,
            InformationRequestPrivacyRequestKind.EXPORT,
            pausingAssembler(interleaving::duringCapture),
        )
        interleaving.awaitCompetitors()

        assertEquals(
            requestIds.sorted(),
            exported.targets.filter { it.outcome == InformationRequestPrivacyTargetOutcome.EXPORTED }
                .map { it.requestId }
                .sorted(),
        )
        requestIds.forEach { requestId ->
            val record = requestRecord(exported, requestId)
            assertCoherent(record)
            assertEquals("ISSUED", stateOf(record))
            assertEquals("CANCELLED", storedState(requestId))
        }
        assertFalse(interleaving.finishedDuringCapture, "Each cancellation waits until the subject export is saved")
    }

    @Test
    fun `a subject export refused during its capture saves no export and lets the waiting change proceed`() {
        val fixture = snapshotFixture()
        val interleaving = Interleaving(listOf(cancelling(fixture.runtime.requestId, fixture.principal)))
        val refusing = pausingAssembler {
            interleaving.duringCapture()
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.PARENT_STATE_INVALID,
                "Parent Exchange not found",
            )
        }

        val refused = privacy(fixture, exportService(fixture, refusing))
            .submit(command(fixture, InformationRequestPrivacyRequestKind.ACCESS))
        interleaving.awaitCompetitors()

        assertEquals(InformationRequestPrivacyRequestState.REFUSED, refused.request.state)
        assertEquals(InformationRequestErrorCatalog.PARENT_STATE_INVALID, refused.request.refusalCode)
        assertNull(refused.request.recordExportId)
        assertTrue(refused.targets.isEmpty())
        assertEquals(0, subjectExportCount(fixture.subjectId))
        assertEquals(0, exportSourceCount(fixture.runtime.requestId))
        assertEquals(0, recordedCount(fixture.subjectId))
        assertFalse(interleaving.finishedDuringCapture, "The cancellation waits until the refused capture rolls back")
        assertEquals("CANCELLED", storedState(fixture.runtime.requestId))
    }

    @Test
    fun `a caller without privacy access is refused without waiting on a source request another command holds`() {
        val fixture = snapshotFixture()
        val denied = mock<InformationRequestOwnerScopeAccess>()
        val owner = ownerOf(fixture)
        whenever(denied.currentOwner()).thenReturn(owner)
        whenever(denied.requireAccess(owner, Action.INFORMATION_REQUEST_MANAGE_PRIVACY))
            .thenThrow(ForbiddenException("Access denied"))
        val executor = Executors.newSingleThreadExecutor()

        val outcome = try {
            dataSource.connection.use { blocker ->
                blocker.autoCommit = false
                lockRow(blocker, "SELECT id FROM exchange WHERE id = ? FOR UPDATE", fixture.runtime.exchangeId)
                lockRow(
                    blocker,
                    "SELECT id FROM information_request WHERE id = ? FOR UPDATE",
                    fixture.runtime.requestId
                )
                CompletableFuture.supplyAsync(
                    {
                        runCatching {
                            privacy(fixture, exportService(fixture, assembler), owners = denied)
                                .submit(command(fixture, InformationRequestPrivacyRequestKind.ACCESS))
                        }
                    },
                    executor,
                ).get(10, TimeUnit.SECONDS).also { blocker.rollback() }
            }
        } finally {
            executor.shutdownNow()
        }

        assertTrue(outcome.exceptionOrNull() is ForbiddenException, outcome.toString())
        assertEquals(0, privacyRequestCount(fixture.subjectId))
        assertEquals(0, subjectExportCount(fixture.subjectId))
    }

    @Test
    fun `a subject export captured during a cancellation withholds confidential items from an unverified caller and discloses them only to a verified one`() {
        val itemId = UUID.randomUUID()
        val fixture = snapshotFixture(confidentialRecord = true) { runtime ->
            val packageId = UUID.randomUUID()
            runtime.insertPackage(packageId, 1)
            runtime.insertItem(
                itemId,
                packageId,
                runtime.documentRequirementId,
                runtime.documentRevisionId,
                runtime.documentBindingId,
                "DOCUMENT",
            )
        }
        val interleaving = Interleaving(listOf(cancelling(fixture.runtime.requestId, fixture.principal)))

        val unverified = subjectExport(
            fixture,
            InformationRequestPrivacyRequestKind.ACCESS,
            pausingAssembler(interleaving::duringCapture),
        )
        interleaving.awaitCompetitors()
        val verified = subjectExport(fixture, InformationRequestPrivacyRequestKind.ACCESS, assembler, verified = true)

        val withheld = requestRecord(unverified, fixture.runtime.requestId)
        assertCoherent(withheld)
        assertEquals("ISSUED", stateOf(withheld))
        assertEquals(JsonPrimitive(true), itemOf(withheld, itemId)["redacted"])
        assertNull(itemOf(withheld, itemId)["disposition"])
        assertFalse(interleaving.finishedDuringCapture, "The cancellation waits until the subject export is saved")
        val disclosed = requestRecord(verified, fixture.runtime.requestId)
        assertCoherent(disclosed)
        assertEquals("CANCELLED", stateOf(disclosed))
        assertNull(itemOf(disclosed, itemId)["redacted"])
        assertEquals("PROVIDED", itemOf(disclosed, itemId).getValue("disposition").jsonPrimitive.content)

        assertFalse(
            readSubjectExport(
                fixture,
                unverified.request.id,
                verifiedReader = false
            ).containsConfidentialContent
        )
        assertThrows(StepUpRequiredException::class.java) {
            readSubjectExport(fixture, verified.request.id, verifiedReader = false)
        }
        assertTrue(readSubjectExport(fixture, verified.request.id, verifiedReader = true).containsConfidentialContent)
    }

    private fun cancellationDuringCapture(kind: InformationRequestPrivacyRequestKind) {
        val fixture = snapshotFixture()
        val interleaving = Interleaving(listOf(cancelling(fixture.runtime.requestId, fixture.principal)))

        val exported = subjectExport(fixture, kind, pausingAssembler(interleaving::duringCapture))
        interleaving.awaitCompetitors()

        assertEquals(InformationRequestPrivacyRequestState.COMPLETED, exported.request.state)
        val record = requestRecord(exported, fixture.runtime.requestId)
        assertCoherent(record)
        assertEquals("ISSUED", stateOf(record))
        assertFalse(interleaving.finishedDuringCapture, "The cancellation waits until the subject export is saved")
        assertEquals("CANCELLED", storedState(fixture.runtime.requestId))
    }

    private inner class Interleaving(private val competitors: List<() -> Any?>) {
        private val executor: ExecutorService = Executors.newCachedThreadPool()
        private var started: List<CompletableFuture<Any?>> = emptyList()

        var finishedDuringCapture: Boolean = false
            private set

        fun duringCapture() {
            if (started.isNotEmpty() || competitors.isEmpty()) return
            started = competitors.map { competitor ->
                CompletableFuture.supplyAsync(
                    { withRequestContext { QuarkusTransaction.requiringNew().call { competitor() } } },
                    executor,
                )
            }
            finishedDuringCapture = CompletableFuture.supplyAsync({ settled(started) }, executor)
                .get(30, TimeUnit.SECONDS)
        }

        fun awaitCompetitors(): List<Any?> {
            try {
                return started.map { it.get(30, TimeUnit.SECONDS) }
            } finally {
                executor.shutdownNow()
            }
        }
    }

    private fun settled(started: List<CompletableFuture<Any?>>): Boolean {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
        while (System.nanoTime() < deadline && started.any { !it.isDone } && lockWaiters() < started.size) {
            Thread.sleep(50)
        }
        return started.all { it.isDone }
    }

    private fun cancelling(requestId: UUID, principal: PrincipalRef): () -> Any? {
        val services = runtime.build(requestId)
        return {
            val request = requireNotNull(requestRepository.findById(requestId))
            services.lifecycle.cancel(
                CancelInformationRequestCommand(
                    requestId = requestId,
                    reasonCode = "record.snapshot.check",
                    access = RequestAccessContext(principal, AuthorizationContext(sessionRef = "owner")),
                    precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.aggregateOf(request)),
                    idempotencyKey = "snapshot-cancel-${UUID.randomUUID()}",
                ),
            )
        }
    }

    private fun pausingAssembler(afterPackagesRead: () -> Unit): InformationRequestRecordAssembler {
        val packages = mock<InformationRequestSubmissionPackageRepository>()
        whenever(packages.findForRequest(any())).thenAnswer { invocation ->
            runtime.packageRepository.findForRequest(invocation.getArgument(0)).also { afterPackagesRead() }
        }
        val original = ClientProxy.unwrap(assembler)
        val type = InformationRequestRecordAssembler::class.java
        val constructor = type.constructors.single { it.parameterCount > 0 }
        val arguments = constructor.parameters.map { parameter ->
            if (parameter.name == PACKAGE_REPOSITORY) packages
            else type.getDeclaredField(parameter.name).apply { isAccessible = true }.get(original)
        }
        return constructor.newInstance(*arguments.toTypedArray()) as InformationRequestRecordAssembler
    }

    private fun assertCoherent(record: JsonObject) {
        val state = stateOf(record)
        val history = record.getValue("history").jsonArray.map { it.jsonObject }
        val historyState = history.lastOrNull()?.getValue("toState")?.jsonPrimitive?.content ?: state
        assertEquals(state, historyState, "The saved export must describe one coherent request snapshot")
        val submissions = history.count { it.getValue("mutation").jsonPrimitive.content == "SUBMIT" }
        assertTrue(
            record.getValue("packages").jsonArray.size >= submissions,
            "Every submission in the saved history must appear among its saved packages",
        )
    }

    private fun stateOf(record: JsonObject): String =
        record.getValue("request").jsonObject.getValue("state").jsonPrimitive.content

    private fun itemOf(record: JsonObject, itemId: UUID): JsonObject =
        record.getValue("packages").jsonArray
            .flatMap { it.jsonObject.getValue("items").jsonArray }
            .map { it.jsonObject }
            .single { it.getValue("itemId").jsonPrimitive.content == itemId.toString() }

    private fun requestRecord(exported: InformationRequestPrivacyRequestView, requestId: UUID): JsonObject =
        exportContent(requireNotNull(exported.request.recordExportId)).getValue("requests").jsonArray
            .map { it.jsonObject }
            .single { it.getValue("request").jsonObject.getValue("id").jsonPrimitive.content == requestId.toString() }

    private fun subjectExport(
        fixture: SubjectExportSnapshotFixture,
        kind: InformationRequestPrivacyRequestKind,
        recordAssembler: InformationRequestRecordAssembler,
        verified: Boolean = false,
    ): InformationRequestPrivacyRequestView =
        privacy(fixture, exportService(fixture, recordAssembler)).submit(command(fixture, kind, verified))

    private fun readSubjectExport(
        fixture: SubjectExportSnapshotFixture,
        privacyRequestId: UUID,
        verifiedReader: Boolean,
    ): InformationRequestRecordExportView =
        QuarkusTransaction.requiringNew().call {
            privacy(fixture, exportService(fixture, assembler), ownerScope(fixture, verifiedReader))
                .subjectExport(privacyRequestId)
        }

    private fun exportService(
        fixture: SubjectExportSnapshotFixture,
        recordAssembler: InformationRequestRecordAssembler,
    ): InformationRequestRecordExportService =
        InformationRequestRecordExportService(
            runtime.build(fixture.runtime.requestId).gate,
            recordAssembler,
            exportRepository,
            requirementRepository,
            storageLocations,
            transfers,
            recordDisposals,
            runtime.commandReceiptService,
            auditRecorder,
            actorLabels,
            readAudit,
            Clock.systemUTC(),
        )

    private fun privacy(
        fixture: SubjectExportSnapshotFixture,
        exports: InformationRequestRecordExportService,
        owners: InformationRequestOwnerScopeAccess = ownerScope(fixture, verified = false),
    ): InformationRequestPrivacyService =
        InformationRequestPrivacyService(
            owners,
            privacyRepository,
            exports,
            restrictions,
            corrections,
            disposals,
            runtime.commandReceiptService,
            auditRecorder,
            actorLabels,
            Clock.systemUTC(),
        )

    private fun ownerScope(
        fixture: SubjectExportSnapshotFixture,
        verified: Boolean
    ): InformationRequestOwnerScopeAccess {
        val access = mock<InformationRequestOwnerScopeAccess>()
        val owner = ownerOf(fixture)
        whenever(access.currentOwner()).thenReturn(owner)
        whenever(access.requireAccess(owner, Action.INFORMATION_REQUEST_MANAGE_PRIVACY)).thenReturn(
            RequestAccessContext(
                fixture.principal,
                AuthorizationContext(sessionRef = "owner-session", mfaSatisfied = verified),
            ),
        )
        return access
    }

    private fun ownerOf(fixture: SubjectExportSnapshotFixture) =
        InformationRequestOwnerRef(InformationRequestOwnerType.ORGANIZATION, fixture.runtime.template.organizationId)

    private fun ownerAccess(fixture: SubjectExportSnapshotFixture) =
        RequestAccessContext(fixture.principal, AuthorizationContext(sessionRef = "owner"))

    private fun party(userId: UUID) =
        RequestAccessContext(PrincipalRef.user(userId), AuthorizationContext(sessionRef = "party-session"))

    private fun command(
        fixture: SubjectExportSnapshotFixture,
        kind: InformationRequestPrivacyRequestKind,
        verified: Boolean = false
    ) =
        RecordInformationRequestPrivacyRequestCommand(
            fixture.subjectId, kind, "subject.request", "policy.basis",
            idempotencyKey = "snapshot-privacy-${UUID.randomUUID()}",
            authorization = AuthorizationContext(sessionRef = "owner-session", mfaSatisfied = verified),
        )

    private fun submissionETag(requestId: UUID): String =
        QuarkusTransaction.requiringNew().call {
            val request = requireNotNull(requestRepository.findById(requestId))
            InformationRequestETag.submissionOf(null, runtime.contentCollector.collect(request, null).contentHash)
        }

    private fun snapshotFixture(
        confidentialRecord: Boolean = false,
        prepare: (SubmissionRuntimeSqlFixture) -> Unit = {},
    ): SubjectExportSnapshotFixture =
        dataSource.connection.use { connection ->
            val runtimeFixture = SubmissionRuntimeSqlFixture(connection, beforePublish = { configured ->
                if (confidentialRecord) {
                    execute(
                        connection,
                        "UPDATE information_request_template_requirement_binding SET confidentiality_compartment_key = 'restricted-record' WHERE id = ?",
                        configured.documentBindingId,
                    )
                }
            })
            val subjectId = UUID.randomUUID()
            runtimeFixture.insertSubject(subjectId, UUID.randomUUID())
            prepare(runtimeFixture)
            SubjectExportSnapshotFixture(runtimeFixture, subjectId)
        }

    private fun subjectParty(connection: Connection, requestId: UUID, subjectId: UUID) {
        execute(
            connection,
            """
            INSERT INTO information_request_party
                (id, information_request_id, role_key, subject_identity_ref_id, active, party_revision,
                 assigned_at, created_at, updated_at)
            VALUES (?, ?, 'SUBJECT', ?, TRUE, 1, now(), now(), now())
            """.trimIndent(),
            UUID.randomUUID(),
            requestId,
            subjectId,
        )
    }

    private fun lockRow(connection: Connection, sql: String, id: UUID) {
        connection.prepareStatement(sql).use { statement ->
            statement.setObject(1, id)
            statement.executeQuery().close()
        }
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

    private fun lockWaiters(): Int =
        dataSource.connection.use {
            queryInt(
                it,
                "SELECT COUNT(*) FROM pg_stat_activity WHERE wait_event_type = 'Lock'"
            )
        }

    private fun exportContent(exportId: UUID): JsonObject =
        dataSource.connection.use { connection ->
            Json.parseToJsonElement(
                requireNotNull(
                    queryString(
                        connection,
                        "SELECT content_json FROM information_request_record_export WHERE id = ?",
                        exportId
                    ),
                ),
            ).jsonObject
        }

    private fun storedState(requestId: UUID): String? =
        dataSource.connection.use { queryString(it, "SELECT state FROM information_request WHERE id = ?", requestId) }

    private fun packageCount(requestId: UUID): Int =
        dataSource.connection.use {
            queryInt(
                it,
                "SELECT count(*) FROM information_request_submission_package WHERE information_request_id = ?",
                requestId
            )
        }

    private fun subjectExportCount(subjectId: UUID): Int =
        dataSource.connection.use {
            queryInt(
                it,
                "SELECT count(*) FROM information_request_record_export WHERE subject_identity_ref_id = ?",
                subjectId
            )
        }

    private fun exportSourceCount(requestId: UUID): Int =
        dataSource.connection.use {
            queryInt(
                it,
                "SELECT count(*) FROM information_request_record_export_source WHERE information_request_id = ?",
                requestId,
            )
        }

    private fun privacyRequestCount(subjectId: UUID): Int =
        dataSource.connection.use {
            queryInt(
                it,
                "SELECT count(*) FROM information_request_privacy_request WHERE subject_identity_ref_id = ?",
                subjectId
            )
        }

    private fun recordedCount(subjectId: UUID): Int =
        dataSource.connection.use {
            queryInt(
                it,
                "SELECT count(*) FROM information_request_privacy_request WHERE subject_identity_ref_id = ? AND state = 'RECORDED'",
                subjectId,
            )
        }

    private companion object {
        const val PACKAGE_REPOSITORY = "packageRepository"
    }
}
