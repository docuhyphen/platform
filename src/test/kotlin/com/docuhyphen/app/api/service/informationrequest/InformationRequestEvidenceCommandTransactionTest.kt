package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.DocumentEncryptionMode
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceCollectionState
import com.docuhyphen.app.api.model.entity.RequestExecutionGrant
import com.docuhyphen.app.api.model.informationrequest.ChangeInformationRequestEvidenceStateCommand
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceAttributes
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceCommandResult
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceFile
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceRequirementState
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceSurface
import com.docuhyphen.app.api.model.informationrequest.ReplaceInformationRequestEvidenceCommand
import com.docuhyphen.app.api.model.informationrequest.UploadInformationRequestEvidenceCommand
import com.docuhyphen.app.api.repository.exchange.ExchangeRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestEvidenceArtifactRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestEvidenceVersionRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestGroupOccurrenceRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateRequirementRepository
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.Decision
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.command.CommandPrecondition
import com.docuhyphen.app.api.service.command.CommandReceiptService
import com.docuhyphen.app.api.service.document.DocumentVersionRecordingService
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestEvidenceCommandTransactionTest
{
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var requestRepository: InformationRequestRepository

    @Inject
    lateinit var exchangeRepository: ExchangeRepository

    @Inject
    lateinit var requirementRepository: InformationRequestRequirementRepository

    @Inject
    lateinit var occurrenceRepository: InformationRequestGroupOccurrenceRepository

    @Inject
    lateinit var templateRequirementRepository: InformationRequestTemplateRequirementRepository

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

    @Inject
    lateinit var evaluationService: InformationRequestEvidenceEvaluationService

    @Inject
    lateinit var deploymentPolicy: InformationRequestEvidenceDeploymentPolicy

    @Inject
    lateinit var contentRelease: InformationRequestEvidenceContentRelease

    @Inject
    lateinit var lockService: InformationRequestSubmissionLockService

    private val respondent = PrincipalRef.participant(UUID.randomUUID())
    private val access = RequestAccessContext(respondent, AuthorizationContext(sessionRef = "verified-session"))

    @Test
    fun `with the shipped configuration and no malware scanner, an uploaded document satisfies its Requirement for every reader`()
    {
        val fixture = InformationRequestEvidencePostgresFixture().insertInto(dataSource)
        val services = services(fixture)

        val uploaded = QuarkusTransaction.requiringNew().call {
            services.uploads.upload(upload(fixture, "document-key", pdfFile()))
        }

        assertTrue(deploymentPolicy.uploadAvailable())
        assertFalse(deploymentPolicy.malwareScanRequired())
        assertFalse(deploymentPolicy.malwareScanningConfigured())
        QuarkusTransaction.requiringNew().run {
            val requirement = requireNotNull(requirementRepository.findById(fixture.requirementId))
            assertEquals(InformationRequestEvidenceRequirementState.SATISFIED, evaluationService.evaluate(requirement)?.state)
            val version = versionRepository.findForArtifact(uploaded.artifact.artifact.id).single()
            val reviewer = RequestAccessContext(PrincipalRef.user(UUID.randomUUID()), AuthorizationContext())
            assertDoesNotThrow { contentRelease.requireReleasable(version, reviewer) }
        }
    }

    @Test
    fun `an upload, a replacement, and a withdrawal persist under the database's evidence invariants`()
    {
        val fixture = InformationRequestEvidencePostgresFixture().insertInto(dataSource)
        val services = services(fixture)

        val uploaded = QuarkusTransaction.requiringNew().call {
            services.uploads.upload(upload(fixture, "upload-key", evidenceFile("first content")))
        }
        val replaced = QuarkusTransaction.requiringNew().call {
            services.uploads.replace(
                ReplaceInformationRequestEvidenceCommand(
                    requestId = fixture.requestId,
                    requirementId = fixture.requirementId,
                    artifactId = uploaded.artifact.artifact.id,
                    access = access,
                    surface = InformationRequestEvidenceSurface.AUTHENTICATED,
                    precondition = CommandPrecondition.ExpectedRevision(uploaded.artifactETag),
                    idempotencyKey = "replace-key",
                    file = evidenceFile("second content"),
                    attributes = InformationRequestEvidenceAttributes(issuer = "Process Registry"),
                ),
            )
        }
        val withdrawn = QuarkusTransaction.requiringNew().call {
            services.collection.withdraw(
                ChangeInformationRequestEvidenceStateCommand(
                    requestId = fixture.requestId,
                    requirementId = fixture.requirementId,
                    artifactId = uploaded.artifact.artifact.id,
                    access = access,
                    precondition = CommandPrecondition.ExpectedRevision(replaced.artifactETag),
                    idempotencyKey = "withdraw-key",
                    reason = "Superseded by another record",
                ),
            )
        }

        QuarkusTransaction.requiringNew().run {
            val artifact = requireNotNull(artifactRepository.findById(uploaded.artifact.artifact.id))
            val versions = versionRepository.findForArtifact(artifact.id)
            val documentVersions = versions.map { requireNotNull(documentVersionRecordingService.findVersion(requireNotNull(it.documentVersionId))) }

            assertEquals(InformationRequestEvidenceCollectionState.WITHDRAWN, artifact.collectionState)
            assertEquals(3L, artifact.artifactRevision)
            assertEquals(listOf(1, 2), versions.map { it.versionNumber })
            assertEquals(listOf("1", "2"), documentVersions.map { it.version })
            assertEquals(1, documentVersions.map { it.document.id }.distinct().size)
            assertEquals(
                listOf("first content", "second content"),
                documentVersions.map { documentVersionRecordingService.open(it).file.readText() },
            )
            assertEquals(0, exchangeDocumentCount(documentVersions.first().document.id))
            assertEquals(
                null,
                exchangeRepository.findDocumentBySessionIdAndDocumentId(fixture.exchangeId, documentVersions.first().document.id),
            )
            assertEquals(withdrawn.artifactETag, InformationRequestETag.artifactOf(artifact))
        }
    }

    @Test
    fun `a failed upload leaves no evidence, document, or receipt behind`()
    {
        val fixture = InformationRequestEvidencePostgresFixture().insertInto(dataSource)
        val history = mock<InformationRequestTransitionHistoryService>()
        whenever(history.record(any())).thenThrow(IllegalStateException("history unavailable"))
        val services = services(fixture, history)
        val uploader = RequestAccessContext(PrincipalRef.participant(UUID.randomUUID()), AuthorizationContext())

        assertThrows(IllegalStateException::class.java)
        {
            QuarkusTransaction.requiringNew().call {
                services.uploads.upload(upload(fixture, "failing-key", evidenceFile("first content"), caller = uploader))
            }
        }

        QuarkusTransaction.requiringNew().run {
            assertTrue(artifactRepository.findForRequest(fixture.requestId).isEmpty())
        }
        assertEquals(0, documentVersionCountCreatedBy(uploader.principal))

        val retried = QuarkusTransaction.requiringNew().call {
            services(fixture).uploads.upload(
                upload(fixture, "failing-key", evidenceFile("first content"), caller = uploader),
            )
        }
        assertEquals("evidence-1", retried.artifact.artifact.artifactKey)
    }

    @Test
    fun `a concurrent retry of the same upload waits for the first and replays it`()
    {
        val fixture = InformationRequestEvidencePostgresFixture().insertInto(dataSource)
        val insideFirst = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val history = mock<InformationRequestTransitionHistoryService>()
        doAnswer {
            insideFirst.countDown()
            releaseFirst.await(30, TimeUnit.SECONDS)
            null
        }.whenever(history).record(any())
        val services = services(fixture, history)
        val file = evidenceFile("duplicate content")
        val executor = Executors.newFixedThreadPool(2)

        try
        {
            val first = executor.submit<InformationRequestEvidenceCommandResult> {
                QuarkusTransaction.requiringNew().call { services.uploads.upload(upload(fixture, "shared-key", file)) }
            }
            assertTrue(insideFirst.await(30, TimeUnit.SECONDS))
            val second = executor.submit<InformationRequestEvidenceCommandResult> {
                QuarkusTransaction.requiringNew().call {
                    services.uploads.upload(
                        upload(fixture, "shared-key", file, precondition = CommandPrecondition.Unconditioned),
                    )
                }
            }
            Thread.sleep(750)
            releaseFirst.countDown()

            val firstResult = first.get(60, TimeUnit.SECONDS)
            val secondResult = second.get(60, TimeUnit.SECONDS)

            assertEquals(firstResult.artifact.artifact.id, secondResult.artifact.artifact.id)
            QuarkusTransaction.requiringNew().run {
                assertEquals(1, artifactRepository.findForRequest(fixture.requestId).size)
            }
        }
        finally
        {
            releaseFirst.countDown()
            executor.shutdownNow()
        }
    }

    private fun services(
        fixture: InformationRequestEvidencePostgresFixture,
        history: InformationRequestTransitionHistoryService = mock(),
    ): EvidenceServices
    {
        val authorizationService = mock<AuthorizationService>()
        whenever(authorizationService.authorize(any(), any(), any(), any())).thenReturn(Decision.Allow())
        val grantService = mock<InformationRequestExecutionGrantService>()
        whenever(grantService.findForRequest(fixture.requestId)).thenReturn(
            RequestExecutionGrant().apply {
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
            exchangeRepository = exchangeRepository,
            requirementRepository = requirementRepository,
            occurrenceRepository = occurrenceRepository,
            templateRequirementRepository = templateRequirementRepository,
            authorizationService = authorizationService,
            entitlementGuard = mock(),
            executionGrantService = grantService,
            lockService = lockService,
        )
        val viewLoader = InformationRequestEvidenceViewLoader(versionRepository, documentVersionRecordingService)
        return EvidenceServices(
            uploads = InformationRequestEvidenceUploadService(
                gate = gate,
                artifactRepository = artifactRepository,
                versionRepository = versionRepository,
                documentVersionRecordingService = documentVersionRecordingService,
                commandReceiptService = commandReceiptService,
                transitionHistory = history,
                viewLoader = viewLoader,
                intake = intake,
            ),
            collection = InformationRequestEvidenceCollectionService(
                gate = gate,
                artifactRepository = artifactRepository,
                commandReceiptService = commandReceiptService,
                transitionHistory = history,
                viewLoader = viewLoader,
            ),
        )
    }

    private fun upload(
        fixture: InformationRequestEvidencePostgresFixture,
        idempotencyKey: String,
        file: InformationRequestEvidenceFile,
        precondition: CommandPrecondition = CommandPrecondition.ExpectedRevision("\"${fixture.requirementId}:0\""),
        caller: RequestAccessContext = access,
    ) = UploadInformationRequestEvidenceCommand(
        requestId = fixture.requestId,
        requirementId = fixture.requirementId,
        access = caller,
        surface = InformationRequestEvidenceSurface.AUTHENTICATED,
        precondition = precondition,
        idempotencyKey = idempotencyKey,
        file = file,
        attributes = InformationRequestEvidenceAttributes.NONE,
    )

    private fun pdfFile(): InformationRequestEvidenceFile
    {
        val target = File.createTempFile("evidence-document", ".pdf").apply { deleteOnExit() }
        PDDocument().use { document ->
            document.addPage(PDPage())
            document.save(target)
        }
        return InformationRequestEvidenceFile(target, "collected-record.pdf", "application/pdf", DocumentEncryptionMode.INTERNAL)
    }

    private fun evidenceFile(content: String): InformationRequestEvidenceFile =
        InformationRequestEvidenceFile(
            File.createTempFile("evidence-upload", ".pdf").apply {
                deleteOnExit()
                writeText(content)
            },
            "collected-record.pdf",
            "application/pdf",
            DocumentEncryptionMode.INTERNAL,
        )

    private fun exchangeDocumentCount(documentId: UUID): Int =
        dataSource.connection.use { connection ->
            connection.prepareStatement("SELECT COUNT(*) FROM exchange_document WHERE documents_id = ?").use { statement ->
                statement.setObject(1, documentId)
                statement.executeQuery().use { rows ->
                    rows.next()
                    rows.getInt(1)
                }
            }
        }

    private fun documentVersionCountCreatedBy(creator: PrincipalRef): Int =
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "SELECT COUNT(*) FROM document_version WHERE created_by_principal_kind = ? AND created_by_principal_id = ?",
            ).use { statement ->
                statement.setString(1, creator.kind.name)
                statement.setObject(2, creator.id)
                statement.executeQuery().use { rows ->
                    rows.next()
                    rows.getInt(1)
                }
            }
        }

    private class EvidenceServices(
        val uploads: InformationRequestEvidenceUploadService,
        val collection: InformationRequestEvidenceCollectionService,
    )
}
