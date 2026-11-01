package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.auth.authz.Decision
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.repository.exchange.ExchangeRepository
import com.docuhyphen.app.api.model.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContextFactory
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.storage.FileStorageService
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.io.File
import java.util.*

class ExchangeDocumentUploadVersionCreatorTest
{
    private val exchangeId = UUID.randomUUID()
    private val documentId = UUID.randomUUID()
    private val actorId = UUID.randomUUID()

    private val exchangeRepository: ExchangeRepository = mock()
    private val shareService: ShareService = mock()
    private val fileStorageService: FileStorageService = mock()
    private val documentContentHashService: DocumentContentHashService = mock()
    private val authorizationService: AuthorizationService = mock()
    private val authorizationContextFactory: AuthorizationContextFactory = mock()
    private val documentVersionService: ExchangeDocumentVersionService = mock()

    private val exchange = Exchange().apply {
        id = exchangeId
        status = ExchangeStatus.ACCEPTED_STARTED
    }

    private val document = Document().apply {
        id = documentId
        title = "Process record"
        type = DocumentType.PDF
    }

    private val file: File = File.createTempFile("uploaded-document", ".pdf").apply {
        deleteOnExit()
        writeText("process record content ".repeat(8))
    }

    init
    {
        whenever(exchangeRepository.findById(exchangeId)).thenReturn(exchange)
        whenever(exchangeRepository.findDocumentBySessionIdAndDocumentId(exchangeId, documentId)).thenReturn(document)
        whenever(documentContentHashService.sha256(any())).thenReturn("content-hash")
        whenever(authorizationService.authorize(any(), any(), any(), any())).thenReturn(Decision.Allow())
        whenever(authorizationContextFactory.currentContext()).thenReturn(AuthorizationContext.ANONYMOUS)
    }

    @Test
    fun `an authenticated upload is recorded as a version created by the authenticated principal`()
    {
        whenever(authorizationContextFactory.currentPrincipal()).thenReturn(PrincipalRef.user(actorId))

        service().uploadDocument(file, ".pdf", exchangeId.toString(), documentId.toString(), DocumentEncryptionMode.INTERNAL)

        verify(documentVersionService).recordUploadedFileAsVersion(
            eq(document),
            eq(file),
            eq(PrincipalRef.user(actorId)),
            eq(DocumentEncryptionMode.INTERNAL),
        )
    }

    @Test
    fun `a no-auth upload is recorded as a version created by the primary recipient principal`()
    {
        val recipient = PrincipalRef.group(UUID.randomUUID())
        whenever(shareService.recipientConstraintAllows(exchangeId, "allow_document_upload")).thenReturn(true)
        whenever(shareService.primaryRecipientPrincipal(exchangeId)).thenReturn(recipient)

        service(authenticated = false).uploadNoAuthDocument(
            file,
            ".pdf",
            exchangeId.toString(),
            documentId.toString(),
            DocumentEncryptionMode.INTERNAL,
            "no-auth-token",
        )

        verify(documentVersionService).recordUploadedFileAsVersion(
            eq(document),
            eq(file),
            eq(recipient),
            eq(DocumentEncryptionMode.INTERNAL),
        )
    }

    @Test
    fun `a no-auth upload with no active primary recipient is refused before content is stored`()
    {
        whenever(shareService.recipientConstraintAllows(exchangeId, "allow_document_upload")).thenReturn(true)
        whenever(shareService.primaryRecipientPrincipal(exchangeId)).thenReturn(null)

        assertThrows(IllegalArgumentException::class.java)
        {
            service(authenticated = false).uploadNoAuthDocument(
                file,
                ".pdf",
                exchangeId.toString(),
                documentId.toString(),
                DocumentEncryptionMode.INTERNAL,
                "no-auth-token",
            )
        }

        verify(fileStorageService, never()).uploadDocument(any(), any())
        verifyNoInteractions(documentVersionService)
    }

    private fun service(authenticated: Boolean = true): ExchangeDocumentService
    {
        val authTokenContext = AuthTokenContext().apply {
            authToken = AuthToken().apply {
                appUser = if (authenticated) AppUser().apply { id = actorId } else null
            }
        }

        return ExchangeDocumentService(
            sessionRepo = exchangeRepository,
            auditService = mock(),
            authTokenContext = authTokenContext,
            emailService = mock(),
            emailTemplateService = mock(),
            configurationService = mock(),
            fileStorageService = fileStorageService,
            documentContentHashService = documentContentHashService,
            documentThumbnailService = mock(),
            documentPdfConversionService = mock(),
            inAppNotificationService = mock(),
            realtimeEventService = mock(),
            shareService = shareService,
            appUserService = mock(),
            authorizationService = authorizationService,
            authorizationContextFactory = authorizationContextFactory,
            auditRecorder = mock(),
            auditOwnerScopeResolver = mock(),
            noAuthExchangeAccessTokenService = mock(),
            noAuthExchangeAccessWindowService = mock(),
            documentVersionService = documentVersionService,
        )
    }
}
