package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.exception.ExchangeDocumentNotFoundException
import com.docuhyphen.app.api.exception.ExchangeNotFoundException
import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.model.entity.AuthToken
import com.docuhyphen.app.api.model.entity.Exchange
import com.docuhyphen.app.api.model.entity.ExchangeStatus
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.model.entity.WorkflowInstance
import com.docuhyphen.app.api.repository.ExchangeRepository
import com.docuhyphen.app.api.repository.WorkflowInstanceRepository
import com.docuhyphen.app.api.service.AppUserService
import com.docuhyphen.app.api.service.UserContactService
import com.docuhyphen.app.api.service.audit.AuditCaptureFailedException
import com.docuhyphen.app.api.service.audit.AuditCaptureResult
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContextFactory
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.Decision
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.communication.EmailService
import com.docuhyphen.app.api.service.communication.EmailTemplateService
import com.docuhyphen.app.api.service.communication.OtpService
import com.docuhyphen.app.api.service.workflow.WorkflowEngineService
import io.quarkus.security.ForbiddenException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

/**
 * Rescind side effects and document-parent path validation.
 *
 * Rules verified:
 *  - An authorized rescind invokes shareService.revokeAllForResource (shares revoked after auth).
 *  - An authorized rescind with running workflow instances invokes workflowEngineService.cancel
 *    for each instance (workflows cancelled after auth).
 *  - A denied rescind produces NO share revocation and NO workflow cancellation.
 *  - Rescinding a ENDED exchange throws IllegalArgumentException after auth passes; side effects
 *    do not occur (authorization does not imply side effects when business rules block the action).
 *  - Rescinding a REJECTED exchange throws IllegalArgumentException after auth passes.
 *  - A document that belongs to Exchange A is denied when accessed via Exchange B's path, even
 *    when both IDs exist (document-parent path mismatch denial).
 */
class RescindSideEffectsTest
{
    private val exchangeId: UUID = UUID.randomUUID()
    private val userId: UUID = UUID.randomUUID()
    private val principal = PrincipalRef.user(userId)

    private fun makeUser(): AppUser = AppUser().apply {
        id = userId
        email = "test@example.com"
        isActive = true
    }

    private fun makeExchange(status: ExchangeStatus = ExchangeStatus.INITIATED): Exchange = Exchange().apply {
        this.status = status
        this.ownerOrganizationId = null
        this.ownerUserId = userId
        this.requireRecipientSignIn = false
    }

    private fun makeTokenContext(user: AppUser): AuthTokenContext
    {
        val ctx = AuthTokenContext()
        ctx.authToken = AuthToken().apply { appUser = user }
        return ctx
    }

    private fun makeFactory(): AuthorizationContextFactory
    {
        val f = mock<AuthorizationContextFactory>()
        whenever(f.currentPrincipal()).thenReturn(principal)
        whenever(f.currentContext()).thenReturn(AuthorizationContext.ANONYMOUS)
        return f
    }

    private fun makeAuthService(vararg allowed: Action): AuthorizationService
    {
        val svc = mock<AuthorizationService>()
        whenever(svc.authorize(any(), any(), any(), any())).thenAnswer { inv ->
            val action = inv.getArgument<Action>(1)
            if (action in allowed) Decision.Allow() else Decision.Deny("test-deny", "denied in test")
        }
        return svc
    }

    private fun makeService(
        authSvc: AuthorizationService = makeAuthService(),
        exchangeRepo: ExchangeRepository = mock(),
        shareService: ShareService = mock(),
        workflowInstanceRepo: WorkflowInstanceRepository = mock<WorkflowInstanceRepository>().also {
            whenever(it.findAllActiveForSubject(any(), any())).thenReturn(emptyList())
        },
        workflowEngineService: WorkflowEngineService = mock(),
        auditRecorder: AuditRecorder = mock<AuditRecorder>().also {
            whenever(it.record(any())).thenReturn(AuditCaptureResult.Captured(UUID.randomUUID(), UUID.randomUUID()))
        },
    ): ExchangeUpdateService = ExchangeUpdateService(
        exchangeRepository = exchangeRepo,
        emailService = mock(),
        emailTemplateService = mock(),
        realtimeEventService = mock(),
        otpService = mock(),
        userContactService = mock(),
        shareService = shareService,
        exchangeRecipientService = mock(),
        externalParticipantRepository = mock(),
        principalGroupRepository = mock(),
        shareRepository = mock(),
        appUserService = mock(),
        workflowInstanceRepository = workflowInstanceRepo,
        workflowStepRepository = mock(),
        workflowEngineService = workflowEngineService,
        authTokenContext = makeTokenContext(makeUser()),
        authorizationService = authSvc,
        authorizationContextFactory = makeFactory(),
        auditRecorder = auditRecorder,
        noAuthExchangeAccessTokenService = mock(),
        noAuthExchangeAccessWindowService = mock(),
        lifecycleNotificationService = mock(),
        documentThumbnailService = mock(),
    )

    // -------------------------------------------------------------------------
    // Authorized rescind side effects
    // -------------------------------------------------------------------------

    @Test
    fun `rescindExchange - authorized - revokes all shares on the exchange`()
    {
        val exchange = makeExchange(ExchangeStatus.INITIATED)
        val repo = mock<ExchangeRepository>()
        whenever(repo.findById(exchangeId)).thenReturn(exchange, exchange)

        val shareService = mock<ShareService>()

        val svc = makeService(
            authSvc = makeAuthService(Action.EXCHANGE_RESCIND),
            exchangeRepo = repo,
            shareService = shareService,
        )

        svc.rescindExchange(exchangeId.toString())

        verify(shareService).revokeAllForResource(ResourceType.EXCHANGE, exchangeId)
    }

    @Test
    fun `rescindExchange - authorized with running workflow - cancels each workflow instance`()
    {
        val instanceId = UUID.randomUUID()
        val instance = WorkflowInstance().apply { id = instanceId }

        val exchange = makeExchange(ExchangeStatus.INITIATED)
        val repo = mock<ExchangeRepository>()
        whenever(repo.findById(exchangeId)).thenReturn(exchange, exchange)

        val workflowInstanceRepo = mock<WorkflowInstanceRepository>()
        whenever(workflowInstanceRepo.findAllActiveForSubject(any(), any())).thenReturn(listOf(instance))

        val workflowEngineService = mock<WorkflowEngineService>()

        val svc = makeService(
            authSvc = makeAuthService(Action.EXCHANGE_RESCIND),
            exchangeRepo = repo,
            workflowInstanceRepo = workflowInstanceRepo,
            workflowEngineService = workflowEngineService,
        )

        svc.rescindExchange(exchangeId.toString())

        verify(workflowEngineService).cancel(instanceId, "Exchange rescinded")
    }

    // -------------------------------------------------------------------------
    // Denied rescind: no side effects
    // -------------------------------------------------------------------------

    @Test
    fun `rescindExchange - denied - does not revoke shares`()
    {
        val exchange = makeExchange(ExchangeStatus.INITIATED)
        val repo = mock<ExchangeRepository>()
        whenever(repo.findById(exchangeId)).thenReturn(exchange)

        val shareService = mock<ShareService>()

        val svc = makeService(
            authSvc = makeAuthService(),
            exchangeRepo = repo,
            shareService = shareService,
        )

        assertThrows<ForbiddenException> { svc.rescindExchange(exchangeId.toString()) }

        verify(shareService, never()).revokeAllForResource(any(), any(), anyOrNull(), anyOrNull())
    }

    // -------------------------------------------------------------------------
    // Terminal-status exchanges: auth passes but business rules block side effects
    // -------------------------------------------------------------------------

    @Test
    fun `rescindExchange ENDED - throws IllegalArgumentException after auth passes`()
    {
        val exchange = makeExchange(ExchangeStatus.ENDED)
        val repo = mock<ExchangeRepository>()
        whenever(repo.findById(exchangeId)).thenReturn(exchange)

        val shareService = mock<ShareService>()

        val svc = makeService(
            authSvc = makeAuthService(Action.EXCHANGE_RESCIND),
            exchangeRepo = repo,
            shareService = shareService,
        )

        assertThrows<IllegalArgumentException> { svc.rescindExchange(exchangeId.toString()) }

        verify(shareService, never()).revokeAllForResource(any(), any(), anyOrNull(), anyOrNull())
    }

    @Test
    fun `rescindExchange REJECTED - throws IllegalArgumentException after auth passes`()
    {
        val exchange = makeExchange(ExchangeStatus.REJECTED)
        val repo = mock<ExchangeRepository>()
        whenever(repo.findById(exchangeId)).thenReturn(exchange)

        val shareService = mock<ShareService>()

        val svc = makeService(
            authSvc = makeAuthService(Action.EXCHANGE_RESCIND),
            exchangeRepo = repo,
            shareService = shareService,
        )

        assertThrows<IllegalArgumentException> { svc.rescindExchange(exchangeId.toString()) }

        verify(shareService, never()).revokeAllForResource(any(), any(), anyOrNull(), anyOrNull())
    }

    // Audit capture failure behavior during rescind operations.

    @Test
    fun `rescindExchange - audit capture fails closed - propagates and does not revoke shares or cancel workflows`()
    {
        val instance = WorkflowInstance().apply { id = UUID.randomUUID() }

        val exchange = makeExchange(ExchangeStatus.INITIATED)
        val repo = mock<ExchangeRepository>()
        whenever(repo.findById(exchangeId)).thenReturn(exchange, exchange)

        val workflowInstanceRepo = mock<WorkflowInstanceRepository>()
        whenever(workflowInstanceRepo.findAllActiveForSubject(any(), any())).thenReturn(listOf(instance))

        val workflowEngineService = mock<WorkflowEngineService>()
        val shareService = mock<ShareService>()

        val auditRecorder = mock<AuditRecorder>()
        whenever(auditRecorder.record(any())).thenThrow(AuditCaptureFailedException("capture failed", null))

        val svc = makeService(
            authSvc = makeAuthService(Action.EXCHANGE_RESCIND),
            exchangeRepo = repo,
            shareService = shareService,
            workflowInstanceRepo = workflowInstanceRepo,
            workflowEngineService = workflowEngineService,
            auditRecorder = auditRecorder,
        )

        assertThrows<AuditCaptureFailedException> { svc.rescindExchange(exchangeId.toString()) }

        verify(shareService, never()).revokeAllForResource(any(), any(), anyOrNull(), anyOrNull())
        verify(workflowEngineService, never()).cancel(any(), any())
    }

    @Test
    fun `rescindExchange - audit capture degraded - still completes rescind side effects`()
    {
        val exchange = makeExchange(ExchangeStatus.INITIATED)
        val repo = mock<ExchangeRepository>()
        whenever(repo.findById(exchangeId)).thenReturn(exchange, exchange)

        val shareService = mock<ShareService>()

        val auditRecorder = mock<AuditRecorder>()
        whenever(auditRecorder.record(any()))
            .thenReturn(AuditCaptureResult.Degraded(UUID.randomUUID(), "db unavailable"))

        val svc = makeService(
            authSvc = makeAuthService(Action.EXCHANGE_RESCIND),
            exchangeRepo = repo,
            shareService = shareService,
            auditRecorder = auditRecorder,
        )

        svc.rescindExchange(exchangeId.toString())

        verify(shareService).revokeAllForResource(ResourceType.EXCHANGE, exchangeId)
    }

    // -------------------------------------------------------------------------
    // Document-parent path validation
    // -------------------------------------------------------------------------

    @Test
    fun `downloadDocument - document belonging to different exchange throws ExchangeDocumentNotFoundException`()
    {
        val exchangeBId = UUID.randomUUID()
        val documentFromAId = UUID.randomUUID()

        val exchangeB = Exchange().apply {
            id = exchangeBId
            status = ExchangeStatus.ACCEPTED_STARTED
            requireRecipientSignIn = false
        }

        val repo = mock<ExchangeRepository>()
        whenever(repo.findById(exchangeBId)).thenReturn(exchangeB)
        whenever(repo.findDocumentBySessionIdAndDocumentId(exchangeBId, documentFromAId)).thenReturn(null)

        val authSvc = mock<AuthorizationService>()
        whenever(authSvc.authorize(any(), any(), any(), any())).thenReturn(Decision.Allow())
        val factory = mock<AuthorizationContextFactory>()
        whenever(factory.currentContext()).thenReturn(AuthorizationContext.ANONYMOUS)

        val user = makeUser()
        val ctx = AuthTokenContext()
        ctx.authToken = AuthToken().apply { appUser = user }

        val svc = ExchangeDocumentService(
            sessionRepo = repo,
            auditService = mock(),
            authTokenContext = ctx,
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
            authorizationService = authSvc,
            authorizationContextFactory = factory,
            auditRecorder = mock(),
            noAuthExchangeAccessTokenService = mock(),
            noAuthExchangeAccessWindowService = mock(),
            documentVersionService = mock(),
        )

        assertThrows<ExchangeDocumentNotFoundException> {
            svc.downloadDocument(exchangeBId.toString(), documentFromAId.toString())
        }
    }
}
