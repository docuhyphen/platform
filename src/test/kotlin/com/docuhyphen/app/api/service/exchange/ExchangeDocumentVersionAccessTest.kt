package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.exception.ExchangeDocumentNotFoundException
import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.document.DocumentVersionContentHashAlgorithm
import com.docuhyphen.app.api.model.document.DocumentVersionContentVerification
import com.docuhyphen.app.api.model.document.DocumentVersionLocatorKind
import com.docuhyphen.app.api.model.document.DocumentVersionStorageProvider
import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.model.entity.AuthToken
import com.docuhyphen.app.api.model.entity.Document
import com.docuhyphen.app.api.model.entity.DocumentEncryptionMode
import com.docuhyphen.app.api.model.entity.DocumentType
import com.docuhyphen.app.api.model.entity.DocumentVersion
import com.docuhyphen.app.api.model.entity.Exchange
import com.docuhyphen.app.api.model.entity.ExchangeStatus
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.repository.exchange.DocumentVersionRepository
import com.docuhyphen.app.api.repository.exchange.ExchangeRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContextFactory
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.Decision
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import com.docuhyphen.app.api.model.identity.PrincipalDisplay
import com.docuhyphen.app.api.service.identity.PrincipalDisplayService
import com.docuhyphen.app.api.service.document.DocumentVersionRecordingService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

class ExchangeDocumentVersionAccessTest
{
    private val exchangeId = UUID.randomUUID()
    private val documentId = UUID.randomUUID()
    private val foreignDocumentId = UUID.randomUUID()
    private val actorId = UUID.randomUUID()

    private val exchangeRepository: ExchangeRepository = mock()
    private val documentVersionRepository: DocumentVersionRepository = mock()
    private val authorizationService: AuthorizationService = mock()
    private val authorizationContextFactory: AuthorizationContextFactory = mock()
    private val documentVersionRecordingService: DocumentVersionRecordingService = mock()
    private val principalDisplayService: PrincipalDisplayService = mock()

    private val exchange = Exchange().apply {
        id = exchangeId
        status = ExchangeStatus.ACCEPTED_STARTED
    }

    private val document = document(documentId)
    private val foreignDocument = document(foreignDocumentId)
    private val ownVersion = version(document)
    private val foreignVersion = version(foreignDocument)

    init
    {
        whenever(exchangeRepository.findById(exchangeId)).thenReturn(exchange)
        whenever(exchangeRepository.findDocumentBySessionIdAndDocumentId(exchangeId, documentId)).thenReturn(document)
        whenever(exchangeRepository.findDocumentBySessionIdAndDocumentId(exchangeId, foreignDocumentId)).thenReturn(null)
        whenever(documentVersionRepository.findByDocumentId(documentId)).thenReturn(listOf(ownVersion))
        whenever(documentVersionRepository.findByDocumentId(foreignDocumentId)).thenReturn(listOf(foreignVersion))
        whenever(documentVersionRepository.findLatestByDocumentId(documentId)).thenReturn(ownVersion)
        whenever(documentVersionRepository.findLatestByDocumentId(foreignDocumentId)).thenReturn(foreignVersion)
        whenever(documentVersionRepository.findById(ownVersion.id)).thenReturn(ownVersion)
        whenever(documentVersionRepository.findById(foreignVersion.id)).thenReturn(foreignVersion)
        whenever(authorizationService.authorize(any(), any(), any(), any())).thenReturn(Decision.Allow())
        whenever(authorizationContextFactory.currentContext()).thenReturn(AuthorizationContext.ANONYMOUS)
        whenever(principalDisplayService.display(any())).thenReturn(PrincipalDisplay(name = null, email = "author@process.test"))
    }

    @Test
    fun `the exchange's own document lists its versions when viewing its documents is permitted`()
    {
        val versions = service().getDocumentVersions(exchangeId.toString(), documentId.toString())

        assertEquals(listOf(ownVersion.id), versions.map { it.version.id })
        verify(authorizationService).authorize(
            any(),
            eq(Action.DOCUMENT_VIEW),
            eq(ResourceRef.exchange(exchangeId)),
            any(),
        )
    }

    @Test
    fun `the version list of a document that is not one of the exchange's documents is refused`()
    {
        assertThrows(ExchangeDocumentNotFoundException::class.java)
        {
            service().getDocumentVersions(exchangeId.toString(), foreignDocumentId.toString())
        }
        verify(documentVersionRepository, never()).findByDocumentId(foreignDocumentId)
    }

    @Test
    fun `the version list is refused without permission to view the exchange's documents`()
    {
        whenever(authorizationService.authorize(any(), eq(Action.DOCUMENT_VIEW), any(), any()))
            .thenReturn(Decision.Deny("NO_CAPABILITY", "Document view is not granted"))

        assertThrows(IllegalArgumentException::class.java)
        {
            service().getDocumentVersions(exchangeId.toString(), documentId.toString())
        }
        verify(documentVersionRepository, never()).findByDocumentId(documentId)
    }

    @Test
    fun `the latest version of a document that is not one of the exchange's documents is refused`()
    {
        assertThrows(ExchangeDocumentNotFoundException::class.java)
        {
            service().getLatestVersion(exchangeId.toString(), foreignDocumentId.toString())
        }
        verify(documentVersionRepository, never()).findLatestByDocumentId(foreignDocumentId)
    }

    @Test
    fun `the latest version is refused without permission to view the exchange's documents`()
    {
        whenever(authorizationService.authorize(any(), eq(Action.DOCUMENT_VIEW), any(), any()))
            .thenReturn(Decision.Deny("NO_CAPABILITY", "Document view is not granted"))

        assertThrows(IllegalArgumentException::class.java)
        {
            service().getLatestVersion(exchangeId.toString(), documentId.toString())
        }
        verify(documentVersionRepository, never()).findLatestByDocumentId(documentId)
    }

    @Test
    fun `a version of a document that is not one of the exchange's documents is not downloaded`()
    {
        assertThrows(ExchangeDocumentNotFoundException::class.java)
        {
            service().getVersionContent(
                exchangeId.toString(),
                foreignDocumentId.toString(),
                foreignVersion.id.toString(),
            )
        }
        verify(documentVersionRecordingService, never()).open(any())
    }

    @Test
    fun `a version that belongs to another document is not downloaded through this document`()
    {
        assertThrows(IllegalArgumentException::class.java)
        {
            service().getVersionContent(exchangeId.toString(), documentId.toString(), foreignVersion.id.toString())
        }
        verify(documentVersionRecordingService, never()).open(any())
    }

    @Test
    fun `a version is not created for a document that is not one of the exchange's documents`()
    {
        assertThrows(ExchangeDocumentNotFoundException::class.java)
        {
            service().createVersion(
                exchangeId.toString(),
                foreignDocumentId.toString(),
                File.createTempFile("uploaded-version", ".pdf").apply { deleteOnExit() },
                DocumentEncryptionMode.INTERNAL,
            )
        }
        verify(documentVersionRecordingService, never()).recordVersion(any(), any(), any())
    }

    private fun service(): ExchangeDocumentVersionService
    {
        val authTokenContext = AuthTokenContext().apply {
            authToken = AuthToken().apply { appUser = AppUser().apply { id = actorId } }
        }

        return ExchangeDocumentVersionService(
            exchangeRepository = exchangeRepository,
            documentVersionRepository = documentVersionRepository,
            documentAuditService = mock(),
            authTokenContext = authTokenContext,
            authorizationService = authorizationService,
            authorizationContextFactory = authorizationContextFactory,
            auditRecorder = mock(),
            auditOwnerScopeResolver = mock(),
            exchangeFeatureSubscriptionGuard = mock(),
            documentVersionRecordingService = documentVersionRecordingService,
            principalDisplayService = principalDisplayService,
        )
    }

    private fun document(id: UUID) = Document().apply {
        this.id = id
        title = "Process record"
        type = DocumentType.PDF
    }

    private fun version(owner: Document) = DocumentVersion().apply {
        document = owner
        fileName = "Process record_v1.pdf"
        storageProvider = DocumentVersionStorageProvider.OBJECT_STORE
        storageLocatorKind = DocumentVersionLocatorKind.OBJECT_KEY
        storageLocator = "document-versions/${owner.id}/$id/Process_record_v1.pdf"
        version = "1"
        createdDate = Timestamp.from(Instant.now())
        createdByPrincipalKind = PrincipalKind.USER
        createdByPrincipalId = actorId
        contentLength = 3
        contentHashAlgorithm = DocumentVersionContentHashAlgorithm.SHA_256
        contentHash = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
        contentVerification = DocumentVersionContentVerification.VERIFIED
    }
}
