package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.exception.WorkflowConflictException
import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.auth.authz.Decision
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.InformationRequestAuditActor
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.workflow.TriggerResult
import com.docuhyphen.app.api.repository.exchange.ExchangeRepository
import com.docuhyphen.app.api.repository.workflow.WorkflowInstanceRepository
import com.docuhyphen.app.api.resource.model.UpdateExchangeRequest
import com.docuhyphen.app.api.service.audit.AuditCaptureResult
import com.docuhyphen.app.api.service.audit.AuditOwnerScope
import com.docuhyphen.app.api.service.audit.AuditOwnerScopeResolver
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.service.auth.authz.*
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.parent.InformationRequestExchangeCompletionException
import com.docuhyphen.app.api.service.informationrequest.parent.InformationRequestExchangeCompletionService
import com.docuhyphen.app.api.service.informationrequest.parent.InformationRequestParentLifecycleService
import com.docuhyphen.app.api.service.workflow.WorkflowEngineService
import io.quarkus.security.ForbiddenException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import java.util.*

class ExchangeTerminationInformationRequestEffectsTest
{
    private val exchangeId: UUID = UUID.randomUUID()
    private val userId: UUID = UUID.randomUUID()
    private val principal = PrincipalRef.user(userId)
    private val ownerContext = AuthorizationContext(sessionRef = "exchange-owner-session")
    private val requestActor = InformationRequestAuditActor.of(RequestAccessContext(principal, ownerContext))

    @Test
    fun `rescinding an Exchange applies rescission to its Information Requests before revoking Exchange shares`()
    {
        val fixture = Fixture(ExchangeStatus.ACCEPTED_STARTED, Action.EXCHANGE_RESCIND)

        fixture.service.rescindExchange(exchangeId.toString())

        val effects = inOrder(fixture.parentLifecycle, fixture.shareService)
        effects.verify(fixture.parentLifecycle).apply(exchangeId, ExchangeStatus.RESCINDED, false, requestActor)
        effects.verify(fixture.shareService)
            .revokeAllForResource(eq(ResourceType.EXCHANGE), eq(exchangeId), anyOrNull(), anyOrNull())
    }

    @Test
    fun `deleting an Exchange applies deletion to its Information Requests before revoking Exchange shares`()
    {
        val fixture = Fixture(ExchangeStatus.ACCEPTED_STARTED, Action.EXCHANGE_DELETE)

        fixture.service.deleteExchange(exchangeId.toString())

        val effects = inOrder(fixture.parentLifecycle, fixture.shareService)
        effects.verify(fixture.parentLifecycle).apply(exchangeId, ExchangeStatus.ACCEPTED_STARTED, true, requestActor)
        effects.verify(fixture.shareService)
            .revokeAllForResource(eq(ResourceType.EXCHANGE), eq(exchangeId), anyOrNull(), anyOrNull())
    }

    @Test
    fun `rejecting an Exchange applies rejection to its Information Requests`()
    {
        val fixture = Fixture(ExchangeStatus.INITIATED, Action.EXCHANGE_ACCEPT, Action.EXCHANGE_VIEW)

        fixture.service.decideAcceptance(exchangeId.toString(), accepted = false, reason = "not required")

        verify(fixture.parentLifecycle).apply(
            exchangeId,
            ExchangeStatus.REJECTED,
            false,
            requestActor,
            ExchangeStatus.INITIATED
        )
    }

    @Test
    fun `ending an Exchange applies its read-only effect to Information Requests`()
    {
        val fixture = Fixture(ExchangeStatus.ACCEPTED_STARTED, Action.EXCHANGE_VIEW, Action.EXCHANGE_EDIT)

        fixture.service.updateExchange(exchangeId.toString(), UpdateExchangeRequest(status = ExchangeStatus.ENDED))

        verify(fixture.parentLifecycle).apply(
            exchangeId,
            ExchangeStatus.ENDED,
            false,
            requestActor,
            ExchangeStatus.ACCEPTED_STARTED
        )
    }

    @Test
    fun `ending an Exchange directly checks its Information Requests first and cancels the remaining ones only as it ends`()
    {
        val fixture = Fixture(ExchangeStatus.ACCEPTED_STARTED, Action.EXCHANGE_VIEW, Action.EXCHANGE_EDIT)

        fixture.service.updateExchange(
            exchangeId.toString(),
            UpdateExchangeRequest(status = ExchangeStatus.ENDED, cancelRemainingInformationRequests = true),
        )

        val effects = inOrder(
            fixture.requestCompletion,
            fixture.workflowEngine,
            fixture.exchangeRepository,
            fixture.parentLifecycle
        )
        effects.verify(fixture.requestCompletion).checkEnding(eq(fixture.exchange), eq(true), anyOrNull())
        effects.verify(fixture.workflowEngine).trigger(any())
        effects.verify(fixture.requestCompletion)
            .prepareEnding(eq(fixture.exchange), eq(requestActor), eq(true), anyOrNull(), isNull())
        effects.verify(fixture.exchangeRepository).updateStatus(exchangeId, ExchangeStatus.ENDED)
        effects.verify(fixture.parentLifecycle)
            .apply(exchangeId, ExchangeStatus.ENDED, false, requestActor, ExchangeStatus.ACCEPTED_STARTED)
        verify(fixture.endingIntents, never()).record(any(), any(), any(), any())
    }

    @Test
    fun `an ending Workflow that starts leaves every request unchanged and keeps the stated cancellation choice for its confirmation`() {
        val fixture = Fixture(ExchangeStatus.ACCEPTED_STARTED, Action.EXCHANGE_VIEW, Action.EXCHANGE_EDIT)
        val instanceId = UUID.randomUUID()
        whenever(fixture.workflowEngine.trigger(any()))
            .thenReturn(TriggerResult(instanceId, UUID.randomUUID(), UUID.randomUUID(), emptyList()))

        val held = assertThrows<WorkflowConflictException> {
            fixture.service.updateExchange(
                exchangeId.toString(),
                UpdateExchangeRequest(status = ExchangeStatus.ENDED, cancelRemainingInformationRequests = true),
            )
        }

        assertEquals(WorkflowConflictException.EXCHANGE_ENDING_HELD_BY_WORKFLOW, held.reasonCode)
        verify(fixture.requestCompletion).checkEnding(eq(fixture.exchange), eq(true), anyOrNull())
        verify(fixture.requestCompletion, never()).prepareEnding(any(), any(), any(), anyOrNull(), anyOrNull())
        verify(fixture.endingIntents).record(exchangeId, instanceId, true, principal)
        verify(fixture.exchangeRepository, never()).updateStatus(any(), any())
        verify(fixture.parentLifecycle, never()).apply(any(), any(), any(), any(), anyOrNull())
    }

    @Test
    fun `an Exchange whose Information Requests refuse ending neither starts an ending workflow nor ends`()
    {
        val fixture = Fixture(ExchangeStatus.ACCEPTED_STARTED, Action.EXCHANGE_VIEW, Action.EXCHANGE_EDIT)
        doThrow(
            InformationRequestExchangeCompletionException(
                InformationRequestErrorCatalog.COMPLETION_GATES_UNSATISFIED,
                listOf(UUID.randomUUID()),
            ),
        ).whenever(fixture.requestCompletion).checkEnding(any(), any(), anyOrNull())

        assertThrows<InformationRequestExchangeCompletionException> {
            fixture.service.updateExchange(exchangeId.toString(), UpdateExchangeRequest(status = ExchangeStatus.ENDED))
        }

        verify(fixture.workflowEngine, never()).trigger(any())
        verify(fixture.requestCompletion, never()).prepareEnding(any(), any(), any(), anyOrNull(), anyOrNull())
        verify(fixture.parentLifecycle, never()).apply(any(), any(), any(), any(), anyOrNull())
        verify(fixture.exchangeRepository, never()).updateStatus(any(), any())
    }

    @Test
    fun `requiring recipient sign-in cuts off the Exchange's Information Request access links`() {
        val fixture = Fixture(ExchangeStatus.ACCEPTED_STARTED, Action.EXCHANGE_VIEW, Action.EXCHANGE_EDIT)

        fixture.service.updateExchange(exchangeId.toString(), UpdateExchangeRequest(requireRecipientSignIn = true))

        verify(fixture.parentLifecycle).enforceRecipientSignIn(exchangeId, requestActor)
    }

    @Test
    fun `allowing access without sign-in cuts nothing off`() {
        val fixture = Fixture(ExchangeStatus.ACCEPTED_STARTED, Action.EXCHANGE_VIEW, Action.EXCHANGE_EDIT)

        fixture.service.updateExchange(exchangeId.toString(), UpdateExchangeRequest(requireRecipientSignIn = false))

        verify(fixture.parentLifecycle, never()).enforceRecipientSignIn(any(), any())
    }

    @Test
    fun `a refused rescind applies no Information Request effects`()
    {
        val fixture = Fixture(ExchangeStatus.ACCEPTED_STARTED, Action.EXCHANGE_VIEW)

        assertThrows<ForbiddenException> { fixture.service.rescindExchange(exchangeId.toString()) }

        verify(fixture.parentLifecycle, never()).apply(any(), any(), any(), any(), anyOrNull())
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
        val requestCompletion = mock<InformationRequestExchangeCompletionService>()
        val endingIntents = mock<ExchangeEndingIntentService>()
        val workflowEngine = mock<WorkflowEngineService>()
        val shareService = mock<ShareService>()
        val exchangeRepository = mock<ExchangeRepository>()
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
            whenever(authorizationContextFactory.currentContext()).thenReturn(ownerContext)

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
                workflowEngineService = workflowEngine,
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
                requestCompletion = requestCompletion,
                endingIntents = endingIntents,
            )
        }
    }
}

