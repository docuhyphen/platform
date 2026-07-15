package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.model.entity.AuthToken
import com.docuhyphen.app.api.model.entity.Document
import com.docuhyphen.app.api.model.entity.DocumentType
import com.docuhyphen.app.api.model.entity.Exchange
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.repository.ExchangeRepository
import com.docuhyphen.app.api.service.AppUserService
import com.docuhyphen.app.api.service.audit.AuditCaptureResult
import com.docuhyphen.app.api.service.audit.AuditEventDraft
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.service.audit.catalog.AuditActorKind
import com.docuhyphen.app.api.service.audit.catalog.AuditEventType
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContextFactory
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.Decision
import com.docuhyphen.app.api.service.communication.AppNotificationService
import com.docuhyphen.app.api.service.communication.EmailService
import com.docuhyphen.app.api.service.communication.EmailTemplateService
import com.docuhyphen.app.api.service.config.ConfigurationService
import com.docuhyphen.app.api.service.storage.FileStorageService
import com.docuhyphen.app.api.realtime.RealtimeEventService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File
import java.util.UUID

/**
 * Verifies that [ExchangeDocumentService.downloadDocument]
 * must call [AuditRecorder.record] exactly once with DOCUMENT_DOWNLOAD/EXCHANGE/HUMAN, and must
 * never let an [AuditRecorder] failure break the actual file download response.
 */
class ExchangeDocumentServiceAuditTest
{
    private val exchangeId = UUID.randomUUID()
    private val documentId = UUID.randomUUID()
    private val actorId = UUID.randomUUID()
    private val orgId = UUID.randomUUID()

    private fun exchange() = Exchange().apply {
        id = exchangeId
        ownerOrganizationId = orgId
    }

    private fun document() = Document().apply {
        id = documentId
        title = "Contract.pdf"
        type = DocumentType.PDF
    }

    private fun service(
        auditRecorder: AuditRecorder,
        exchangeRepository: ExchangeRepository,
        shareService: ShareService = mock(),
        fileStorageService: FileStorageService = mock(),
    ): ExchangeDocumentService
    {
        val authTokenContext = AuthTokenContext()
        authTokenContext.authToken = AuthToken().apply {
            appUser = AppUser().apply { id = actorId }
        }

        val authorizationService = mock<AuthorizationService>()
        whenever(authorizationService.authorize(any(), any(), any(), any())).thenReturn(Decision.Allow())
        val authorizationContextFactory = mock<AuthorizationContextFactory>()

        return ExchangeDocumentService(
            sessionRepo = exchangeRepository,
            auditService = mock(),
            authTokenContext = authTokenContext,
            emailService = mock<EmailService>(),
            emailTemplateService = mock<EmailTemplateService>(),
            configurationService = mock<ConfigurationService>(),
            fileStorageService = fileStorageService,
            inAppNotificationService = mock(),
            realtimeEventService = mock<RealtimeEventService>(),
            shareService = shareService,
            appUserService = mock<AppUserService>(),
            authorizationService = authorizationService,
            authorizationContextFactory = authorizationContextFactory,
            auditRecorder = auditRecorder,
            noAuthExchangeAccessTokenService = mock(),
        )
    }

    @Test
    fun `downloadDocument records exactly one DOCUMENT_DOWNLOAD event with HUMAN actor kind`()
    {
        val auditRecorder = mock<AuditRecorder>()
        whenever(auditRecorder.record(any())).thenReturn(AuditCaptureResult.Captured(UUID.randomUUID(), UUID.randomUUID()))

        val exchangeRepository = mock<ExchangeRepository>()
        whenever(exchangeRepository.findById(exchangeId)).thenReturn(exchange())
        whenever(exchangeRepository.findDocumentBySessionIdAndDocumentId(exchangeId, documentId)).thenReturn(document())

        val fileStorageService = mock<FileStorageService>()
        whenever(fileStorageService.downloadDocument(any())).thenReturn(File("dummy"))

        val service = service(auditRecorder, exchangeRepository, fileStorageService = fileStorageService)

        service.downloadDocument(exchangeId.toString(), documentId.toString())

        val captor = argumentCaptor<AuditEventDraft>()
        verify(auditRecorder).record(captor.capture())
        assertEquals(AuditEventType.DOCUMENT_DOWNLOAD.key, captor.firstValue.eventTypeKey)
        assertEquals(ResourceType.DOCUMENT.name, captor.firstValue.targetType)
        assertEquals(documentId.toString(), captor.firstValue.targetId)
        assertEquals(AuditActorKind.HUMAN, captor.firstValue.actorKind)
        assertEquals(actorId, captor.firstValue.actorId)
    }

    @Test
    fun `downloadDocument still returns the file when AuditRecorder throws`()
    {
        val auditRecorder = mock<AuditRecorder>()
        whenever(auditRecorder.record(any()))
            .thenThrow(com.docuhyphen.app.api.service.audit.AuditCaptureFailedException("boom", null))

        val exchangeRepository = mock<ExchangeRepository>()
        whenever(exchangeRepository.findById(exchangeId)).thenReturn(exchange())
        whenever(exchangeRepository.findDocumentBySessionIdAndDocumentId(exchangeId, documentId)).thenReturn(document())

        val expectedFile = File("dummy")
        val fileStorageService = mock<FileStorageService>()
        whenever(fileStorageService.downloadDocument(any())).thenReturn(expectedFile)

        val service = service(auditRecorder, exchangeRepository, fileStorageService = fileStorageService)

        val result = service.downloadDocument(exchangeId.toString(), documentId.toString())

        assertEquals(expectedFile, result)
        verify(auditRecorder).record(any())
    }
}
