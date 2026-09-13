package com.docuhyphen.app.api.service.exchange.permutation

import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.realtime.RealtimeEventService
import com.docuhyphen.app.api.repository.exchange.ExchangeRepository
import com.docuhyphen.app.api.repository.exchange.ExternalParticipantRepository
import com.docuhyphen.app.api.repository.exchange.ShareRepository
import com.docuhyphen.app.api.repository.organization.PrincipalGroupRepository
import com.docuhyphen.app.api.repository.workflow.WorkflowInstanceRepository
import com.docuhyphen.app.api.repository.workflow.WorkflowStepInstanceRepository
import com.docuhyphen.app.api.service.audit.AuditOwnerScope
import com.docuhyphen.app.api.service.audit.AuditOwnerScopeResolver
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.service.auth.authz.*
import com.docuhyphen.app.api.service.communication.EmailService
import com.docuhyphen.app.api.service.communication.EmailTemplateService
import com.docuhyphen.app.api.service.communication.OtpService
import com.docuhyphen.app.api.service.contactdetails.UserContactService
import com.docuhyphen.app.api.service.exchange.*
import com.docuhyphen.app.api.service.user.AppUserService
import com.docuhyphen.app.api.service.workflow.Decision
import com.docuhyphen.app.api.service.workflow.WorkflowEngineService
import org.mockito.kotlin.*
import java.util.*
import com.docuhyphen.app.api.service.auth.authz.Decision as AuthorizationDecision

internal class ExchangeAcceptanceWorkflowFixture
{
    val exchangeId: UUID = UUID.randomUUID()
    val recipientUserId: UUID = UUID.randomUUID()
    val exchange: Exchange = Exchange().apply {
        id = exchangeId
        name = "Workflow acceptance Exchange"
        status = ExchangeStatus.INITIATED
        ownerUserId = UUID.randomUUID()
    }
    val workflowInstance: WorkflowInstance = WorkflowInstance().apply {
        subjectResourceId = exchangeId
        triggerEventSnapshot = "exchange.acceptance_pending"
    }
    val workflowStep: WorkflowStepInstance = WorkflowStepInstance().apply {
        instanceId = workflowInstance.id
        stepIndex = workflowInstance.currentStepIndex
        specSnapshotJson = "{}"
    }
    val exchangeRepository: ExchangeRepository = mock()
    val workflowInstanceRepository: WorkflowInstanceRepository = mock()
    val workflowStepRepository: WorkflowStepInstanceRepository = mock()
    val workflowEngineService: WorkflowEngineService = mock()
    val exchangeRecipientService: ExchangeRecipientService = mock()
    val shareService: ShareService = mock()
    val service: ExchangeUpdateService

    init
    {
        val appUser = AppUser().apply {
            id = recipientUserId
            email = "workflow-recipient@example.test"
            isActive = true
        }
        val authTokenContext = AuthTokenContext().apply {
            authToken = AuthToken().apply { this.appUser = appUser }
        }
        val principal = PrincipalRef.user(recipientUserId)
        val authorizationContextFactory = mock<AuthorizationContextFactory>()
        val authorizationService = mock<AuthorizationService>()

        whenever(authorizationContextFactory.currentPrincipal()).thenReturn(principal)
        whenever(authorizationContextFactory.currentContext()).thenReturn(AuthorizationContext.ANONYMOUS)
        whenever(authorizationService.authorize(eq(principal), eq(Action.EXCHANGE_ACCEPT), any(), any()))
            .thenReturn(AuthorizationDecision.Allow())
        whenever(exchangeRepository.findById(exchangeId)).thenReturn(exchange)
        whenever(exchangeRepository.findByIdForUpdate(exchangeId)).thenReturn(exchange)
        whenever(
            workflowInstanceRepository.findActiveForSubjectAndTrigger(
                exchangeId,
                "exchange.draft_submitted",
            ),
        ).thenReturn(null)
        whenever(
            workflowInstanceRepository.findActiveForSubjectAndTrigger(
                exchangeId,
                "exchange.acceptance_pending",
            ),
        ).thenReturn(workflowInstance)
        whenever(
            workflowStepRepository.findCurrent(
                workflowInstance.id,
                workflowInstance.currentStepIndex,
            ),
        ).thenReturn(workflowStep)

        service = ExchangeUpdateService(
            exchangeRepository = exchangeRepository,
            emailService = mock<EmailService>(),
            emailTemplateService = mock<EmailTemplateService>(),
            realtimeEventService = mock<RealtimeEventService>(),
            otpService = mock<OtpService>(),
            userContactService = mock<UserContactService>(),
            shareService = shareService,
            exchangeRecipientService = exchangeRecipientService,
            externalParticipantRepository = mock<ExternalParticipantRepository>(),
            principalGroupRepository = mock<PrincipalGroupRepository>(),
            shareRepository = mock<ShareRepository>(),
            appUserService = mock<AppUserService>(),
            workflowInstanceRepository = workflowInstanceRepository,
            workflowStepRepository = workflowStepRepository,
            workflowEngineService = workflowEngineService,
            authTokenContext = authTokenContext,
            authorizationService = authorizationService,
            authorizationContextFactory = authorizationContextFactory,
            auditRecorder = mock<AuditRecorder>(),
            auditOwnerScopeResolver = mock<AuditOwnerScopeResolver>().also {
                whenever(it.resolve(any(), any())).thenReturn(AuditOwnerScope.Platform)
            },
            noAuthExchangeAccessTokenService = mock<NoAuthExchangeAccessTokenService>(),
            noAuthExchangeAccessWindowService = mock(),
            lifecycleNotificationService = mock<ExchangeLifecycleNotificationService>(),
            documentThumbnailService = mock<DocumentThumbnailService>(),
        requestParentLifecycle = mock(),
        )
    }

    fun decide(accepted: Boolean, reason: String? = null)
    {
        service.decideAcceptance(exchangeId.toString(), accepted, reason)
    }

    fun verifyWorkflowDecision(decision: Decision, reason: String?)
    {
        verify(workflowEngineService).recordDecision(
            workflowStep.id,
            PrincipalRef.user(recipientUserId),
            decision,
            reason,
        )
    }
}
