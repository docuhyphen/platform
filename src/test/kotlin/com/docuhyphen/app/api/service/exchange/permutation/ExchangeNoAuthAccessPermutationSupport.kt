package com.docuhyphen.app.api.service.exchange.permutation

import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.dto.NoAuthExchangeBasicDto
import com.docuhyphen.app.api.model.entity.Document
import com.docuhyphen.app.api.model.entity.DocumentType
import com.docuhyphen.app.api.model.entity.Exchange
import com.docuhyphen.app.api.model.entity.ExchangeStatus
import com.docuhyphen.app.api.realtime.RealtimeEventService
import com.docuhyphen.app.api.repository.exchange.ExchangeRepository
import com.docuhyphen.app.api.service.audit.AuditCaptureResult
import com.docuhyphen.app.api.service.audit.AuditOwnerScope
import com.docuhyphen.app.api.service.audit.AuditOwnerScopeResolver
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContextFactory
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.communication.EmailService
import com.docuhyphen.app.api.service.communication.EmailTemplateService
import com.docuhyphen.app.api.service.communication.OtpService
import com.docuhyphen.app.api.service.config.ConfigurationService
import com.docuhyphen.app.api.service.contactdetails.UserContactService
import com.docuhyphen.app.api.service.exchange.*
import com.docuhyphen.app.api.service.notification.InAppNotificationService
import com.docuhyphen.app.api.service.storage.FileStorageService
import com.docuhyphen.app.api.service.user.AppUserService
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File
import java.sql.Timestamp
import java.time.Instant
import java.util.*

internal class NoAuthRetrievalFixture(
    status: ExchangeStatus = ExchangeStatus.INITIATED,
    requireSignIn: Boolean = false,
)
{
    val exchange: Exchange = Exchange().apply {
        this.status = status
        requireRecipientSignIn = requireSignIn
        documents = mutableListOf()
    }
    private val repository = mock<ExchangeRepository>()
    private val shareService = mock<ShareService>()
    private val tokenService = NoAuthExchangeAccessTokenService()
    private val service = ExchangeRetrievalService(
        exchangeRepository = repository,
        authTokenContext = mock(),
        authorizationService = mock(),
        authorizationContextFactory = mock(),
        shareService = shareService,
        exchangeRecipientService = mock(),
        noAuthExchangeAccessTokenService = tokenService,
        noAuthExchangeAccessWindowService = NoAuthExchangeAccessWindowService(repository, mock()),
    )
    val accessToken: String = tokenService.issue(exchange)

    init
    {
        whenever(repository.findByIdWithDocumentsOrderedByTitle(exchange.id)).thenReturn(exchange)
        whenever(shareService.recipientConstraintsJson(exchange.id)).thenReturn("""{"can_download":true}""")
    }

    fun retrieve(token: String? = accessToken): NoAuthExchangeBasicDto =
        service.getNoAuthExchange(exchange.id.toString(), token)
}

internal class NoAuthOtpVerificationFixture(
    expiry: Instant?,
    storedHash: String? = "stored-hash",
)
{
    val exchange: Exchange = Exchange().apply {
        status = ExchangeStatus.INITIATED
        requireRecipientSignIn = false
        recipientOtpHash = storedHash
        recipientOtpExpiry = expiry?.let(Timestamp::from)
        documents = mutableListOf()
    }
    private val repository = mock<ExchangeRepository>()
    private val otpService = mock<OtpService>()
    private val shareService = mock<ShareService>()
    private val tokenService = NoAuthExchangeAccessTokenService()
    private val service: ExchangeUpdateService
    private val accessToken: String = tokenService.issue(exchange)

    init
    {
        whenever(repository.findById(exchange.id)).thenReturn(exchange)
        whenever(repository.findByIdForUpdate(exchange.id)).thenReturn(exchange)
        whenever(otpService.verifyEmailOtp(eq("123456"), eq("stored-hash"))).thenReturn(true)
        whenever(shareService.recipientConstraintsJson(exchange.id)).thenReturn("{}")
        service = ExchangeUpdateService(
            exchangeRepository = repository,
            emailService = mock<EmailService>(),
            emailTemplateService = mock<EmailTemplateService>(),
            realtimeEventService = mock<RealtimeEventService>(),
            otpService = otpService,
            userContactService = mock<UserContactService>(),
            shareService = shareService,
            exchangeRecipientService = mock<ExchangeRecipientService>(),
            externalParticipantRepository = mock(),
            principalGroupRepository = mock(),
            shareRepository = mock(),
            appUserService = mock<AppUserService>(),
            workflowInstanceRepository = mock(),
            workflowStepRepository = mock(),
            workflowEngineService = mock(),
            authTokenContext = mock<AuthTokenContext>(),
            authorizationService = mock<AuthorizationService>(),
            authorizationContextFactory = mock<AuthorizationContextFactory>(),
            auditRecorder = mock<AuditRecorder>(),
            auditOwnerScopeResolver = mock<AuditOwnerScopeResolver>().also {
                whenever(it.resolve(any(), any())).thenReturn(AuditOwnerScope.Platform)
            },
            noAuthExchangeAccessTokenService = tokenService,
            noAuthExchangeAccessWindowService = NoAuthExchangeAccessWindowService(repository, mock()),
            lifecycleNotificationService = mock<ExchangeLifecycleNotificationService>(),
            documentThumbnailService = mock<DocumentThumbnailService>(),
        requestParentLifecycle = mock(),
        )
    }

    fun verify(otp: String? = "123456"): NoAuthExchangeBasicDto =
        service.verifyNoAuthAccessCode(exchange.id.toString(), otp, accessToken)

}

internal class NoAuthDocumentAccessFixture(
    verifiedAt: Instant?,
    validityDays: Int = 7,
    status: ExchangeStatus = ExchangeStatus.ACCEPTED_STARTED,
    requireSignIn: Boolean = false,
)
{
    val exchange: Exchange = Exchange().apply {
        this.status = status
        requireRecipientSignIn = requireSignIn
        noAuthAccessValidityDays = validityDays
    }
    private val document = Document().apply {
        title = "shared.pdf"
        type = DocumentType.PDF
        isDeleted = false
    }
    private val expectedFile = File("shared.pdf")
    private val repository = mock<ExchangeRepository>()
    private val shareService = mock<ShareService>()
    private val fileStorageService = mock<FileStorageService>()
    private val tokenService = NoAuthExchangeAccessTokenService()
    private val service: ExchangeDocumentService
    private val accessToken: String = tokenService.issue(exchange)

    init
    {
        exchange.noAuthAccessVerifiedAt = verifiedAt?.let(Timestamp::from)
        whenever(repository.findById(exchange.id)).thenReturn(exchange)
        whenever(repository.findByIdForUpdate(exchange.id)).thenReturn(exchange)
        whenever(repository.findDocumentBySessionIdAndDocumentId(exchange.id, document.id)).thenReturn(document)
        whenever(shareService.recipientConstraintsJson(exchange.id)).thenReturn("""{"can_download":true}""")
        whenever(fileStorageService.downloadDocument(any())).thenReturn(expectedFile)
        val auditRecorder = mock<AuditRecorder>()
        whenever(auditRecorder.record(any()))
            .thenReturn(AuditCaptureResult.Captured(UUID.randomUUID(), UUID.randomUUID()))
        service = ExchangeDocumentService(
            sessionRepo = repository,
            auditService = mock<ExchangeDocumentAuditService>(),
            authTokenContext = mock<AuthTokenContext>(),
            emailService = mock<EmailService>(),
            emailTemplateService = mock<EmailTemplateService>(),
            configurationService = mock<ConfigurationService>(),
            fileStorageService = fileStorageService,
            documentContentHashService = mock<DocumentContentHashService>(),
            documentThumbnailService = mock<DocumentThumbnailService>(),

            documentPdfConversionService = mock<DocumentPdfConversionService>(),
            inAppNotificationService = mock<InAppNotificationService>(),
            realtimeEventService = mock<RealtimeEventService>(),
            shareService = shareService,
            appUserService = mock<AppUserService>(),
            authorizationService = mock<AuthorizationService>(),
            authorizationContextFactory = mock<AuthorizationContextFactory>(),
            auditRecorder = auditRecorder,
            auditOwnerScopeResolver = mock<AuditOwnerScopeResolver>().also {
                whenever(it.resolve(any(), any())).thenReturn(AuditOwnerScope.Platform)
            },
            noAuthExchangeAccessTokenService = tokenService,
            noAuthExchangeAccessWindowService = NoAuthExchangeAccessWindowService(repository, mock()),
            documentVersionService = mock(),
        )
    }

    fun download(): File = service.downloadNoAuthSessionDocument(
        exchange.id.toString(),
        document.id.toString(),
        accessToken,
    )
}
