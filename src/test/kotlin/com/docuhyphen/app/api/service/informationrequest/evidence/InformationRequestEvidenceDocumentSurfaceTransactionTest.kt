package com.docuhyphen.app.api.service.informationrequest.evidence

import com.docuhyphen.app.api.exception.ExchangeDocumentNotFoundException
import com.docuhyphen.app.api.exception.ExchangeNotFoundException
import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.auth.authz.Decision
import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.model.entity.AuthToken
import com.docuhyphen.app.api.model.entity.DocumentEncryptionMode
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.evidence.InformationRequestEvidenceAttributes
import com.docuhyphen.app.api.model.informationrequest.evidence.InformationRequestEvidenceFile
import com.docuhyphen.app.api.model.informationrequest.evidence.InformationRequestEvidenceSurface
import com.docuhyphen.app.api.model.informationrequest.evidence.UploadInformationRequestEvidenceCommand
import com.docuhyphen.app.api.repository.exchange.ExchangeRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.evidence.InformationRequestEvidenceArtifactRepository
import com.docuhyphen.app.api.repository.informationrequest.evidence.InformationRequestEvidenceVersionRepository
import com.docuhyphen.app.api.repository.informationrequest.occurrence.InformationRequestGroupOccurrenceRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateRequirementRepository
import com.docuhyphen.app.api.model.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContextFactory
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.command.CommandReceiptService
import com.docuhyphen.app.api.service.document.DocumentVersionRecordingService
import com.docuhyphen.app.api.service.exchange.*
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestContinuationGuard
import com.docuhyphen.app.api.service.informationrequest.condition.InformationRequestConditionVisibility
import com.docuhyphen.app.api.service.informationrequest.execution.InformationRequestExecutionGrantService
import com.docuhyphen.app.api.service.informationrequest.execution.issuedExecutionGrant
import com.docuhyphen.app.api.service.informationrequest.noauth.InformationRequestNoAuthAccessDoubles
import com.docuhyphen.app.api.service.informationrequest.submission.InformationRequestSubmissionLockService
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File
import java.sql.Timestamp
import java.time.Instant
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestEvidenceDocumentSurfaceTransactionTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var requestRepository: InformationRequestRepository

    @Inject
    lateinit var exchangeRepository: ExchangeRepository

    @Inject
    lateinit var exchangeLookup: ExchangeLookupService

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
    lateinit var lockService: InformationRequestSubmissionLockService

    @Test
    fun `uploaded evidence is never one of the Exchange's Documents on any ordinary Document surface`() {
        val fixture = InformationRequestEvidencePostgresFixture().insertInto(dataSource)
        val uploaded = QuarkusTransaction.requiringNew().call { uploads(fixture).upload(upload(fixture)) }
        val (evidenceDocumentId, evidenceVersionId) = QuarkusTransaction.requiringNew().call {
            val documentVersionId = requireNotNull(
                versionRepository.findForArtifact(uploaded.artifact.artifact.id).single().documentVersionId
            )
            val documentVersion = requireNotNull(documentVersionRecordingService.findVersion(documentVersionId))
            requireNotNull(documentVersion.document).id to documentVersion.id
        }
        val ordinaryDocumentId = ordinaryExchangeDocument(fixture)
        val exchangeId = fixture.exchangeId.toString()
        val evidenceDocument = evidenceDocumentId.toString()
        val versions = versionService(fixture)
        val documents = documentService(fixture, versions)
        val comments = commentsService(fixture, versions)
        val retrieval = retrievalService(fixture)

        QuarkusTransaction.requiringNew().run {
            assertEquals(0, versions.getDocumentVersions(exchangeId, ordinaryDocumentId.toString()).size)
            assertTrue(retrieval.hasDocumentInExchange(fixture.exchangeId, ordinaryDocumentId))

            assertThrows(ExchangeDocumentNotFoundException::class.java) {
                versions.getDocumentVersions(
                    exchangeId,
                    evidenceDocument
                )
            }
            assertThrows(ExchangeDocumentNotFoundException::class.java) {
                versions.getLatestVersion(
                    exchangeId,
                    evidenceDocument
                )
            }
            assertThrows(ExchangeDocumentNotFoundException::class.java)
            {
                versions.getVersionContent(exchangeId, evidenceDocument, evidenceVersionId.toString())
            }
            assertThrows(ExchangeDocumentNotFoundException::class.java) {
                documents.downloadDocument(
                    exchangeId,
                    evidenceDocument
                )
            }
            assertThrows(ExchangeDocumentNotFoundException::class.java)
            {
                documents.getDocumentFilePreviewAsPdf(exchangeId, evidenceDocument)
            }
            assertThrows(ExchangeDocumentNotFoundException::class.java) {
                documents.getDocumentThumbnail(
                    exchangeId,
                    evidenceDocument
                )
            }
            assertThrows(ExchangeNotFoundException::class.java) {
                comments.getDocumentComments(
                    exchangeId,
                    evidenceDocument
                )
            }
            assertFalse(retrieval.hasDocumentInExchange(fixture.exchangeId, evidenceDocumentId))
            assertNull(retrieval.getDocumentTitleForDisplay(fixture.exchangeId, evidenceDocumentId))
        }
    }

    private fun ordinaryExchangeDocument(fixture: InformationRequestEvidencePostgresFixture): UUID {
        val documentId = UUID.randomUUID()
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                INSERT INTO document (id, encryption_mode, is_deleted, created_date, update_date, title, type)
                VALUES (?, 0, FALSE, now(), now(), 'Process record', 'PDF')
                """.trimIndent(),
            ).use { statement ->
                statement.setObject(1, documentId)
                statement.executeUpdate()
            }
            connection.prepareStatement("INSERT INTO exchange_document (exchange_id, documents_id) VALUES (?, ?)")
                .use { statement ->
                    statement.setObject(1, fixture.exchangeId)
                    statement.setObject(2, documentId)
                    statement.executeUpdate()
                }
        }
        return documentId
    }

    private fun ownerContext(fixture: InformationRequestEvidencePostgresFixture) = AuthTokenContext().apply {
        authToken = AuthToken().apply { appUser = AppUser().apply { id = fixture.userId } }
    }

    private fun allowAll(): AuthorizationService =
        mock<AuthorizationService>().also {
            whenever(
                it.authorize(
                    any(),
                    any(),
                    any(),
                    any()
                )
            ).thenReturn(Decision.Allow())
        }

    private fun contextFactory(fixture: InformationRequestEvidencePostgresFixture): AuthorizationContextFactory =
        mock<AuthorizationContextFactory>().also {
            whenever(it.currentContext()).thenReturn(AuthorizationContext.ANONYMOUS)
            whenever(it.currentPrincipal()).thenReturn(PrincipalRef.user(fixture.userId))
        }

    private fun versionService(fixture: InformationRequestEvidencePostgresFixture) =
        ExchangeDocumentVersionService(
            exchangeRepository = exchangeRepository,
            documentAuditService = mock(),
            authTokenContext = ownerContext(fixture),
            authorizationService = allowAll(),
            authorizationContextFactory = contextFactory(fixture),
            auditRecorder = mock(),
            auditOwnerScopeResolver = mock(),
            exchangeFeatureSubscriptionGuard = mock(),
            documentVersionRecordingService = documentVersionRecordingService,
            principalDisplayService = mock(),
        )

    private fun documentService(
        fixture: InformationRequestEvidencePostgresFixture,
        versions: ExchangeDocumentVersionService
    ) =
        ExchangeDocumentService(
            sessionRepo = exchangeRepository,
            auditService = mock(),
            authTokenContext = ownerContext(fixture),
            emailService = mock(),
            emailTemplateService = mock(),
            configurationService = mock(),
            fileStorageService = mock(),
            documentContentHashService = mock(),
            documentThumbnailService = mock(),
            documentPdfConversionService = mock(),
            inAppNotificationService = mock(),
            realtimeEventService = mock(),
            shareService = mock(),
            appUserService = mock(),
            authorizationService = allowAll(),
            authorizationContextFactory = contextFactory(fixture),
            auditRecorder = mock(),
            auditOwnerScopeResolver = mock(),
            noAuthExchangeAccessTokenService = mock(),
            noAuthExchangeAccessWindowService = mock(),
            documentVersionService = versions,
        )

    private fun commentsService(
        fixture: InformationRequestEvidencePostgresFixture,
        versions: ExchangeDocumentVersionService
    ) =
        ExchangeDocumentCommentsService(
            exchangeRepository = exchangeRepository,
            inAppNotificationService = mock(),
            exchangeDocumentAuditService = mock(),
            authTokenContext = ownerContext(fixture),
            documentCommentRepository = mock(),
            organizationMembershipService = mock(),
            shareService = mock(),
            authorizationService = allowAll(),
            authorizationContextFactory = contextFactory(fixture),
            exchangeDocumentVersionService = versions,
            documentCommentRealtimeService = mock(),
        )

    private fun retrievalService(fixture: InformationRequestEvidencePostgresFixture) =
        ExchangeRetrievalService(
            exchangeRepository = exchangeRepository,
            authTokenContext = ownerContext(fixture),
            authorizationService = allowAll(),
            authorizationContextFactory = contextFactory(fixture),
            shareService = mock(),
            exchangeRecipientService = mock(),
            noAuthExchangeAccessTokenService = mock(),
            noAuthExchangeAccessWindowService = mock(),
        )

    private fun uploads(fixture: InformationRequestEvidencePostgresFixture): InformationRequestEvidenceUploadService {
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
            authorizationService = allowAll(),
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
            transitionHistory = mock(),
            viewLoader = InformationRequestEvidenceViewLoader(versionRepository, documentVersionRecordingService),
            intake = intake,
            noAuthAccess = InformationRequestNoAuthAccessDoubles.current(),
        )
    }

    private fun upload(fixture: InformationRequestEvidencePostgresFixture) =
        UploadInformationRequestEvidenceCommand(
            requestId = fixture.requestId,
            requirementId = fixture.requirementId,
            access = RequestAccessContext(
                PrincipalRef.participant(UUID.randomUUID()),
                AuthorizationContext(sessionRef = "verified-session")
            ),
            surface = InformationRequestEvidenceSurface.AUTHENTICATED,
            precondition = CommandPrecondition.ExpectedRevision("\"${fixture.requirementId}:0\""),
            idempotencyKey = "surface-upload",
            file = InformationRequestEvidenceFile(
                File.createTempFile("evidence-surface", ".pdf").apply {
                    deleteOnExit()
                    writeText("%PDF-1.4\n%%EOF\n")
                },
                "collected-record.pdf",
                "application/pdf",
                DocumentEncryptionMode.INTERNAL,
            ),
            attributes = InformationRequestEvidenceAttributes.NONE,
        )
}
