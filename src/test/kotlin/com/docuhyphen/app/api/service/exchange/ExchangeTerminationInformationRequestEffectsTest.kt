package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.model.entity.AuthToken
import com.docuhyphen.app.api.model.entity.Exchange
import com.docuhyphen.app.api.model.entity.ExchangeStatus
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.repository.exchange.ExchangeRepository
import com.docuhyphen.app.api.repository.workflow.WorkflowInstanceRepository
import com.docuhyphen.app.api.resource.model.UpdateExchangeRequest
import com.docuhyphen.app.api.service.audit.AuditCaptureResult
import com.docuhyphen.app.api.service.audit.AuditOwnerScope
import com.docuhyphen.app.api.service.audit.AuditOwnerScopeResolver
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContextFactory
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.Decision
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.exchange.ExchangeRecipientService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestParentLifecycleService
import io.quarkus.security.ForbiddenException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

class ExchangeTerminationInformationRequestEffectsTest
{
    private val exchangeId: UUID = UUID.randomUUID()
    private val userId: UUID = UUID.randomUUID()
    private val principal = PrincipalRef.user(userId)

    @Test
    fun `rescinding an Exchange applies rescission to its Information Requests before revoking Exchange shares`()
    {
        val fixture = Fixture(ExchangeStatus.ACCEPTED_STARTED, Action.EXCHANGE_RESCIND)

        fixture.service.rescindExchange(exchangeId.toString())

        val effects = inOrder(fixture.parentLifecycle, fixture.shareService)
        effects.verify(fixture.parentLifecycle).apply(exchangeId, ExchangeStatus.RESCINDED, false, principal)
        effects.verify(fixture.shareService)
            .revokeAllForResource(eq(ResourceType.EXCHANGE), eq(exchangeId), anyOrNull(), anyOrNull())
    }

    @Test
    fun `deleting an Exchange applies deletion to its Information Requests before revoking Exchange shares`()
    {
        val fixture = Fixture(ExchangeStatus.ACCEPTED_STARTED, Action.EXCHANGE_DELETE)

        fixture.service.deleteExchange(exchangeId.toString())

        val effects = inOrder(fixture.parentLifecycle, fixture.shareService)
        effects.verify(fixture.parentLifecycle).apply(exchangeId, ExchangeStatus.ACCEPTED_STARTED, true, principal)
        effects.verify(fixture.shareService)
            .revokeAllForResource(eq(ResourceType.EXCHANGE), eq(exchangeId), anyOrNull(), anyOrNull())
    }

    @Test
    fun `rejecting an Exchange applies rejection to its Information Requests`()
    {
        val fixture = Fixture(ExchangeStatus.INITIATED, Action.EXCHANGE_ACCEPT, Action.EXCHANGE_VIEW)

        fixture.service.decideAcceptance(exchangeId.toString(), accepted = false, reason = "not required")

        verify(fixture.parentLifecycle).apply(exchangeId, ExchangeStatus.REJECTED, false, principal)
    }

    @Test
    fun `ending an Exchange applies its read-only effect to Information Requests`()
    {
        val fixture = Fixture(ExchangeStatus.ACCEPTED_STARTED, Action.EXCHANGE_VIEW, Action.EXCHANGE_EDIT)

        fixture.service.updateExchange(exchangeId.toString(), UpdateExchangeRequest(status = ExchangeStatus.ENDED))

        verify(fixture.parentLifecycle).apply(exchangeId, ExchangeStatus.ENDED, false, principal)
    }

    @Test
    fun `a refused rescind applies no Information Request effects`()
    {
        val fixture = Fixture(ExchangeStatus.ACCEPTED_STARTED)

        assertThrows<ForbiddenException> { fixture.service.rescindExchange(exchangeId.toString()) }

        verify(fixture.parentLifecycle, never()).apply(any(), any(), any(), any())
    }

    private inner class Fixture(status: ExchangeStatus, vararg allowed: Action)
    {
        val exchange = Exchange().apply {
            id = exchangeId
            this.status = status
            ownerUserId = userId
            requireRecipientSignIn = false
        }
        val parentLifecycle = mock<InformationRequestParentLifecycleService>()
        val shareService = mock<ShareService>()
        private val exchangeRepository = mock<ExchangeRepository>()
        private val workflowInstanceRepository = mock<WorkflowInstanceRepository>()
        val service: ExchangeUpdateService

        init
        {
            whenever(exchangeRepository.findById(exchangeId)).thenReturn(exchange)
            whenever(exchangeRepository.findByIdForUpdate(exchangeId)).thenReturn(exchange)
            whenever(workflowInstanceRepository.findAllActiveForSubject(any(), any())).thenReturn(emptyList())
            whenever(workflowInstanceRepository.findActiveForSubjectAndTrigger(any(), any())).thenReturn(null)

            val authorizationService = mock<AuthorizationService>()
            whenever(authorizationService.authorize(any(), any(), any(), any())).thenAnswer { invocation ->
                if (invocation.getArgument<Action>(1) in allowed) Decision.Allow()
                else Decision.Deny("test-deny", "denied in test")
            }
            val authorizationContextFactory = mock<AuthorizationContextFactory>()
            whenever(authorizationContextFactory.currentPrincipal()).thenReturn(principal)
            whenever(authorizationContextFactory.currentContext()).thenReturn(AuthorizationContext.ANONYMOUS)

            val auditRecorder = mock<AuditRecorder>()
            whenever(auditRecorder.record(any()))
                .thenReturn(AuditCaptureResult.Captured(UUID.randomUUID(), UUID.randomUUID()))
            val auditOwnerScopeResolver = mock<AuditOwnerScopeResolver>()
            whenever(auditOwnerScopeResolver.resolve(any(), any())).thenReturn(AuditOwnerScope.Platform)

            val tokenContext = AuthTokenContext()
            tokenContext.authToken = AuthToken().apply {
                appUser = AppUser().apply {
                    id = userId
                    email = "party@example.test"
                    isActive = true
                }
            }

            service = ExchangeUpdateService(
                exchangeRepository = exchangeRepository,
                emailService = mock(),
                emailTemplateService = mock(),
                realtimeEventService = mock(),
                otpService = mock(),
                userContactService = mock(),
                shareService = shareService,
                exchangeRecipientService = mock<ExchangeRecipientService>(),
                externalParticipantRepository = mock(),
                principalGroupRepository = mock(),
                shareRepository = mock(),
                appUserService = mock(),
                workflowInstanceRepository = workflowInstanceRepository,
                workflowStepRepository = mock(),
                workflowEngineService = mock(),
                authTokenContext = tokenContext,
                authorizationService = authorizationService,
                authorizationContextFactory = authorizationContextFactory,
                auditRecorder = auditRecorder,
                auditOwnerScopeResolver = auditOwnerScopeResolver,
                noAuthExchangeAccessTokenService = mock(),
                noAuthExchangeAccessWindowService = mock(),
                lifecycleNotificationService = mock(),
                documentThumbnailService = mock(),
                requestParentLifecycle = parentLifecycle,
            )
        }
    }
}

