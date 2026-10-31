package com.docuhyphen.app.api.service.informationrequest.submission

import com.docuhyphen.app.api.migration.SubmissionRuntimeSqlFixture
import com.docuhyphen.app.api.model.auth.authz.Decision
import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.entity.DocumentEncryptionMode
import com.docuhyphen.app.api.model.entity.InformationRequestTransition
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.evidence.InformationRequestEvidenceAttributes
import com.docuhyphen.app.api.model.informationrequest.evidence.InformationRequestEvidenceFile
import com.docuhyphen.app.api.model.informationrequest.evidence.InformationRequestEvidenceSurface
import com.docuhyphen.app.api.model.informationrequest.evidence.UploadInformationRequestEvidenceCommand
import com.docuhyphen.app.api.model.informationrequest.response.InformationRequestResponsePatch
import com.docuhyphen.app.api.model.informationrequest.response.PatchInformationRequestResponsesCommand
import com.docuhyphen.app.api.model.informationrequest.response.ResponseNarrativePatch
import com.docuhyphen.app.api.model.informationrequest.submission.SubmitInformationRequestPackageCommand
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.evidence.InformationRequestEvidenceArtifactRepository
import com.docuhyphen.app.api.repository.informationrequest.evidence.InformationRequestEvidenceVersionRepository
import com.docuhyphen.app.api.repository.informationrequest.occurrence.InformationRequestGroupOccurrenceRepository
import com.docuhyphen.app.api.repository.informationrequest.submission.InformationRequestSubmissionPackageRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateRequirementRepository
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.command.CommandPreconditionException
import com.docuhyphen.app.api.service.command.CommandReceiptService
import com.docuhyphen.app.api.service.document.DocumentVersionRecordingService
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.exchange.ExchangeLookupService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestRuntimeTestServices
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestContinuationGuard
import com.docuhyphen.app.api.service.informationrequest.condition.InformationRequestConditionVisibility
import com.docuhyphen.app.api.service.informationrequest.evidence.InformationRequestEvidenceGate
import com.docuhyphen.app.api.service.informationrequest.evidence.InformationRequestEvidenceIntake
import com.docuhyphen.app.api.service.informationrequest.evidence.InformationRequestEvidenceUploadService
import com.docuhyphen.app.api.service.informationrequest.evidence.InformationRequestEvidenceViewLoader
import com.docuhyphen.app.api.service.informationrequest.execution.InformationRequestExecutionGrantService
import com.docuhyphen.app.api.service.informationrequest.execution.issuedExecutionGrant
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestTransitionHistoryService
import com.docuhyphen.app.api.service.informationrequest.noauth.InformationRequestNoAuthAccessDoubles
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File
import java.sql.Timestamp
import java.time.Instant
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestSubmissionRaceTransactionTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var runtime: InformationRequestRuntimeTestServices

    @Inject
    lateinit var contentCollector: InformationRequestSubmissionContentCollector

    @Inject
    lateinit var packageReader: InformationRequestSubmissionPackageReader

    @Inject
    lateinit var lockService: InformationRequestSubmissionLockService

    @Inject
    lateinit var requestRepository: InformationRequestRepository

    @Inject
    lateinit var exchangeLookup: ExchangeLookupService

    @Inject
    lateinit var requirementRepository: InformationRequestRequirementRepository

    @Inject
    lateinit var occurrenceRepository: InformationRequestGroupOccurrenceRepository

    @Inject
    lateinit var templateRequirementRepository: InformationRequestTemplateRequirementRepository

    @Inject
    lateinit var packageRepository: InformationRequestSubmissionPackageRepository

    @Inject
    lateinit var artifactRepository: InformationRequestEvidenceArtifactRepository

    @Inject
    lateinit var versionRepository: InformationRequestEvidenceVersionRepository

    @Inject
    lateinit var documentVersionRecordingService: DocumentVersionRecordingService

    @Inject
    lateinit var commandReceiptService: CommandReceiptService

    @Inject
    lateinit var intake: InformationRequestEvidenceIntake

    @Test
    fun `a save that commits first makes a submission waiting behind it stale and no package is recorded`() {
        val fixture = fixture()
        val reviewed = submissionETag(fixture)
        val responses = responsesETag(fixture)
        val revisionBefore = responseRevision(fixture)
        val held = HeldHistory()

        val (saved, submitted) = race(
            held,
            first = {
                runtime.build(fixture.requestId, history = held.history).responses.patch(
                    narrative(
                        fixture,
                        responses,
                        "save-first"
                    )
                )
            },
            second = {
                runtime.build(fixture.requestId, history = mock()).submissions.submit(
                    submit(
                        fixture,
                        reviewed,
                        "submit-behind-save"
                    )
                )
            },
        )

        saved.getOrThrow()
        val refusal = submitted.exceptionOrNull() as CommandPreconditionException
        assertEquals(CommandPreconditionException.Kind.STALE, refusal.kind)
        assertNotEquals(reviewed, refusal.currentETag)
        assertEquals(submissionETag(fixture), refusal.currentETag)
        QuarkusTransaction.requiringNew().run {
            assertTrue(packageRepository.findForRequest(fixture.requestId).isEmpty())
        }
        assertEquals(revisionBefore + 1, responseRevision(fixture))
    }

    @Test
    fun `a save that waited behind a submission with the ETag it read before is refused as stale`() {
        val fixture = fixture()
        val reviewed = submissionETag(fixture)
        val responses = responsesETag(fixture)
        val frozenRevision = QuarkusTransaction.requiringNew().call {
            runtime.responseRepository.findAllForRequest(fixture.requestId)
                .single { it.informationRequestRequirementId == fixture.documentRequirementId }
                .responseRevision
        }
        val held = HeldHistory()

        val (submitted, saved) = race(
            held,
            first = {
                runtime.build(fixture.requestId, history = held.history).submissions.submit(
                    submit(
                        fixture,
                        reviewed,
                        "submit-first"
                    )
                )
            },
            second = {
                runtime.build(fixture.requestId, history = mock()).responses.patch(
                    narrative(
                        fixture,
                        responses,
                        "save-behind-submit"
                    )
                )
            },
        )

        submitted.getOrThrow()
        val refusal = saved.exceptionOrNull() as CommandPreconditionException
        assertEquals(CommandPreconditionException.Kind.STALE, refusal.kind)
        QuarkusTransaction.requiringNew().run {
            val packages = packageRepository.findForRequest(fixture.requestId)
            assertEquals(1, packages.size)
            val frozen = packageReader.view(fixture.requestId, packages.single().id).items
                .single { it.informationRequestRequirementId == fixture.documentRequirementId }
            assertEquals(frozenRevision, frozen.responseRevision)
        }
    }

    @Test
    fun `an upload that commits first makes a submission waiting behind it stale and no package is recorded`() {
        val fixture = fixture()
        val reviewed = submissionETag(fixture)
        val evidence = evidenceETag(fixture)
        val held = HeldHistory()

        val (uploaded, submitted) = race(
            held,
            first = { uploads(fixture, held.history).upload(upload(fixture, evidence, "upload-first")) },
            second = {
                runtime.build(fixture.requestId, history = mock()).submissions.submit(
                    submit(
                        fixture,
                        reviewed,
                        "submit-behind-upload"
                    )
                )
            },
        )

        uploaded.getOrThrow()
        val refusal = submitted.exceptionOrNull() as CommandPreconditionException
        assertEquals(CommandPreconditionException.Kind.STALE, refusal.kind)
        assertEquals(submissionETag(fixture), refusal.currentETag)
        QuarkusTransaction.requiringNew().run {
            assertTrue(packageRepository.findForRequest(fixture.requestId).isEmpty())
            assertEquals(2, artifactRepository.findForRequirement(fixture.documentRequirementId).size)
        }
    }

    @Test
    fun `an upload that waited behind a submission of its Requirement is refused as locked`() {
        val fixture = fixture()
        val reviewed = submissionETag(fixture)
        val evidence = evidenceETag(fixture)
        val held = HeldHistory()

        val (submitted, uploaded) = race(
            held,
            first = {
                runtime.build(fixture.requestId, history = held.history).submissions.submit(
                    submit(
                        fixture,
                        reviewed,
                        "submit-before-upload"
                    )
                )
            },
            second = { uploads(fixture, mock()).upload(upload(fixture, evidence, "upload-behind-submit")) },
        )

        submitted.getOrThrow()
        val refusal = uploaded.exceptionOrNull() as InformationRequestLifecycleException
        assertEquals(InformationRequestErrorCatalog.SUBMISSION_LOCKED, refusal.reasonCode)
        QuarkusTransaction.requiringNew().run {
            assertEquals(1, packageRepository.findForRequest(fixture.requestId).size)
            assertEquals(1, artifactRepository.findForRequirement(fixture.documentRequirementId).size)
        }
    }

    private fun race(
        held: HeldHistory,
        first: () -> Any,
        second: () -> Any,
    ): Pair<Result<Any>, Result<Any>> {
        val executor = Executors.newFixedThreadPool(2)
        try {
            val firstRun =
                executor.submit<Result<Any>> { runCatching { QuarkusTransaction.requiringNew().call { first() } } }
            assertTrue(held.entered.await(30, TimeUnit.SECONDS))
            val secondRun =
                executor.submit<Result<Any>> { runCatching { QuarkusTransaction.requiringNew().call { second() } } }
            Thread.sleep(750)
            held.release.countDown()
            return firstRun.get(60, TimeUnit.SECONDS) to secondRun.get(60, TimeUnit.SECONDS)
        } finally {
            held.release.countDown()
            executor.shutdownNow()
        }
    }

    private fun fixture(): SubmissionRuntimeSqlFixture =
        dataSource.connection.use { connection -> SubmissionRuntimeSqlFixture(connection, staged = true) }

    private fun submissionETag(fixture: SubmissionRuntimeSqlFixture): String =
        QuarkusTransaction.requiringNew().call {
            val request = requireNotNull(requestRepository.findById(fixture.requestId))
            InformationRequestETag.submissionOf(
                RECORD_STAGE,
                contentCollector.collect(request, RECORD_STAGE).contentHash
            )
        }

    private fun responsesETag(fixture: SubmissionRuntimeSqlFixture): String =
        QuarkusTransaction.requiringNew().call {
            InformationRequestETag.responsesOf(requireNotNull(requestRepository.findById(fixture.requestId)))
        }

    private fun evidenceETag(fixture: SubmissionRuntimeSqlFixture): String =
        QuarkusTransaction.requiringNew().call {
            InformationRequestETag.evidenceOf(
                fixture.documentRequirementId,
                artifactRepository.findForRequirement(fixture.documentRequirementId),
            )
        }

    private fun responseRevision(fixture: SubmissionRuntimeSqlFixture): Long =
        QuarkusTransaction.requiringNew()
            .call { requireNotNull(requestRepository.findById(fixture.requestId)).responseRevision }

    private fun contributor(fixture: SubmissionRuntimeSqlFixture) =
        RequestAccessContext(
            PrincipalRef.user(fixture.contributorUserId),
            AuthorizationContext(sessionRef = "contributor-session")
        )

    private fun submit(fixture: SubmissionRuntimeSqlFixture, etag: String, key: String) =
        SubmitInformationRequestPackageCommand(
            requestId = fixture.requestId,
            stageKey = RECORD_STAGE,
            access = contributor(fixture),
            precondition = CommandPrecondition.ExpectedRevision(etag),
            idempotencyKey = key,
        )

    private fun narrative(fixture: SubmissionRuntimeSqlFixture, etag: String, key: String) =
        PatchInformationRequestResponsesCommand(
            requestId = fixture.requestId,
            access = contributor(fixture),
            precondition = CommandPrecondition.ExpectedRevision(etag),
            idempotencyKey = key,
            patches = listOf(
                InformationRequestResponsePatch(
                    requirementId = fixture.documentRequirementId,
                    narrative = ResponseNarrativePatch.Set("A note saved during submission"),
                ),
            ),
        )

    private fun upload(fixture: SubmissionRuntimeSqlFixture, etag: String, key: String) =
        UploadInformationRequestEvidenceCommand(
            requestId = fixture.requestId,
            requirementId = fixture.documentRequirementId,
            access = contributor(fixture),
            surface = InformationRequestEvidenceSurface.AUTHENTICATED,
            precondition = CommandPrecondition.ExpectedRevision(etag),
            idempotencyKey = key,
            file = pdfFile(),
            attributes = InformationRequestEvidenceAttributes.NONE,
        )

    private fun uploads(
        fixture: SubmissionRuntimeSqlFixture,
        history: InformationRequestTransitionHistoryService,
    ): InformationRequestEvidenceUploadService {
        val authorization = mock<AuthorizationService>()
        whenever(authorization.authorize(any(), any(), any(), any())).thenReturn(Decision.Allow())
        val grants = mock<InformationRequestExecutionGrantService>()
        whenever(grants.findForRequest(fixture.requestId)).thenReturn(
            issuedExecutionGrant().apply {
                requestId = fixture.requestId
                ownerType = "ORGANIZATION"
                planCode = "BUSINESS"
                subscriptionStatus = "ACTIVE"
                enforcementMode = "ENFORCE"
                issuedAt = Timestamp.from(Instant.now())
            },
        )
        val gate = InformationRequestEvidenceGate(
            requestRepository = requestRepository,
            exchangeLookup = exchangeLookup,
            requirementRepository = requirementRepository,
            occurrenceRepository = occurrenceRepository,
            templateRequirementRepository = templateRequirementRepository,
            authorizationService = authorization,
            continuationGuard = InformationRequestContinuationGuard(grants, mock()),
            lockService = lockService,
            requirementContext = mock(),
            conditionVisibility = InformationRequestConditionVisibility(mock(), mock()),
        )
        return InformationRequestEvidenceUploadService(
            gate = gate,
            artifactRepository = artifactRepository,
            versionRepository = versionRepository,
            documentVersionRecordingService = documentVersionRecordingService,
            commandReceiptService = commandReceiptService,
            transitionHistory = history,
            viewLoader = InformationRequestEvidenceViewLoader(versionRepository, documentVersionRecordingService),
            intake = intake,
            noAuthAccess = InformationRequestNoAuthAccessDoubles.current(),
        )
    }

    private fun pdfFile(): InformationRequestEvidenceFile {
        val target = File.createTempFile("raced-evidence", ".pdf").apply { deleteOnExit() }
        PDDocument().use { document ->
            document.addPage(PDPage())
            document.documentInformation.title = "Raced record ${UUID.randomUUID()}"
            document.save(target)
        }
        return InformationRequestEvidenceFile(
            target,
            "raced-record.pdf",
            "application/pdf",
            DocumentEncryptionMode.INTERNAL
        )
    }

    private class HeldHistory {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val history: InformationRequestTransitionHistoryService = mock()

        init {
            doAnswer {
                entered.countDown()
                release.await(30, TimeUnit.SECONDS)
                InformationRequestTransition()
            }.whenever(history).record(any())
        }
    }

    private companion object {
        const val RECORD_STAGE = "record-stage"
    }
}
