package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.exchange.ExchangeLifecycleActor
import com.docuhyphen.app.api.model.informationrequest.InformationRequestAuditActor
import com.docuhyphen.app.api.model.workflow.WorkflowDecisionRecord
import com.docuhyphen.app.api.repository.exchange.ExchangeRepository
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.parent.InformationRequestExchangeCompletionException
import com.docuhyphen.app.api.service.informationrequest.parent.InformationRequestExchangeCompletionService
import com.docuhyphen.app.api.service.informationrequest.parent.InformationRequestParentLifecycleService
import com.docuhyphen.app.api.model.notification.DomainEvent
import com.docuhyphen.app.api.service.workflow.Decision
import com.docuhyphen.app.api.service.workflow.WorkflowEngineService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.time.Instant
import java.util.*

class ExchangeApprovalEventHandlerEndingTest {
    private val exchange = Exchange().apply {
        id = UUID.randomUUID()
        ownerOrganizationId = UUID.randomUUID()
        status = ExchangeStatus.ACCEPTED_STARTED
        isDeleted = false
    }
    private val instanceId = UUID.randomUUID()
    private val requesterId = UUID.randomUUID()
    private val system = InformationRequestAuditActor.system(PrincipalRef(PrincipalKind.SERVICE_ACCOUNT, UUID(0, 0)))
    private val requester = InformationRequestAuditActor.system(PrincipalRef.user(requesterId))
    private val exchangeRepository = mock<ExchangeRepository>()
    private val requestParentLifecycle = mock<InformationRequestParentLifecycleService>()
    private val requestCompletion = mock<InformationRequestExchangeCompletionService>()
    private val endingIntents = mock<ExchangeEndingIntentService>()
    private val lifecycleAudit = mock<ExchangeLifecycleAudit>()
    private val lifecycleNotifications = mock<ExchangeLifecycleNotificationService>()
    private val workflowEngine = mock<WorkflowEngineService>()
    private val approverId = UUID.randomUUID()
    private val approver = InformationRequestAuditActor.system(PrincipalRef.user(approverId))
    private val handler = ExchangeApprovalEventHandler(
        shareService = mock(),
        exchangeRepository = exchangeRepository,
        workflowEngineService = workflowEngine,
        organizationService = mock(),
        exchangeInitiationService = mock(),
        exchangeParticipantOrgService = mock(),
        lifecycleNotificationService = lifecycleNotifications,
        exchangeRecipientService = mock(),
        requestParentLifecycle = requestParentLifecycle,
        requestCompletion = requestCompletion,
        endingIntents = endingIntents,
        lifecycleAudit = lifecycleAudit,
    )

    init {
        whenever(exchangeRepository.findByIdForUpdate(exchange.id)).thenReturn(exchange)
    }

    @Test
    fun `a confirmed ending applies the cancellation choice stated when it started and consumes it as the Exchange ends`() {
        val intent = intent(cancelRemaining = true)
        whenever(endingIntents.pendingFor(exchange.id, instanceId)).thenReturn(intent)

        handler.handle(confirmation())

        verify(requestCompletion).prepareEnding(exchange, requester, true, null, intent.createdAt.toInstant())
        verify(endingIntents).consume(intent)
        assertEquals(ExchangeStatus.ENDED, exchange.status)
        verify(requestParentLifecycle).apply(exchange.id, ExchangeStatus.ENDED, false, requester)
        verify(lifecycleAudit).recordTransition(
            exchange.id,
            exchange.name,
            ExchangeStatus.ACCEPTED_STARTED,
            ExchangeStatus.ENDED,
            ExchangeLifecycleActor.WORKFLOW,
        )
    }

    @Test
    fun `a confirmation with no stated choice cancels nothing that remains`() {
        handler.handle(confirmation())

        verify(requestCompletion).prepareEnding(exchange, system, false)
        verify(endingIntents, never()).consume(any())
        assertEquals(ExchangeStatus.ENDED, exchange.status)
    }

    @Test
    fun `a refused confirmation leaves the Exchange active and keeps the stated choice`() {
        val intent = intent(cancelRemaining = false)
        whenever(endingIntents.pendingFor(exchange.id, instanceId)).thenReturn(intent)
        doThrow(
            InformationRequestExchangeCompletionException(
                InformationRequestErrorCatalog.REMAINING_REQUESTS_REQUIRE_CANCELLATION,
                listOf(UUID.randomUUID()),
            ),
        ).whenever(requestCompletion).prepareEnding(exchange, requester, false, null, intent.createdAt.toInstant())

        handler.handle(confirmation())

        assertEquals(ExchangeStatus.ACCEPTED_STARTED, exchange.status)
        verify(endingIntents, never()).consume(any())
        verify(requestParentLifecycle, never()).apply(any(), any(), any(), any(), anyOrNull())
        verify(lifecycleAudit, never()).recordTransition(any(), anyOrNull(), any(), any(), any())
    }

    @Test
    fun `a refused confirmation is recorded and told to the requester and the approvers`() {
        val intent = intent(cancelRemaining = false)
        val blockingRequest = UUID.randomUUID()
        whenever(endingIntents.pendingFor(exchange.id, instanceId)).thenReturn(intent)
        whenever(workflowEngine.decisionsOf(instanceId)).thenReturn(listOf(approval(approverId)))
        doThrow(
            InformationRequestExchangeCompletionException(
                InformationRequestErrorCatalog.REMAINING_REQUESTS_REQUIRE_CANCELLATION,
                listOf(blockingRequest),
            ),
        ).whenever(requestCompletion).prepareEnding(exchange, requester, false, null, intent.createdAt.toInstant())

        handler.handle(confirmation())

        assertEquals(ExchangeStatus.ACCEPTED_STARTED, exchange.status)
        verify(lifecycleAudit).recordEndingRefused(
            exchange.id,
            exchange.name,
            InformationRequestErrorCatalog.REMAINING_REQUESTS_REQUIRE_CANCELLATION,
            1,
            instanceId,
        )
        verify(lifecycleNotifications).publishEndingRefused(exchange, setOf(requesterId, approverId))
    }

    @Test
    fun `a confirmation that states no ending choice is attributed to the approver who confirmed it`() {
        whenever(workflowEngine.decisionsOf(instanceId)).thenReturn(listOf(approval(approverId)))

        handler.handle(confirmation())

        verify(requestCompletion).prepareEnding(exchange, approver, false)
        verify(requestParentLifecycle).apply(exchange.id, ExchangeStatus.ENDED, false, approver)
    }

    @Test
    fun `a confirmation that reaches a finished Exchange changes and records nothing`() {
        exchange.status = ExchangeStatus.RESCINDED

        handler.handle(confirmation())

        assertEquals(ExchangeStatus.RESCINDED, exchange.status)
        verify(exchangeRepository, never()).update(any())
        verifyNoInteractions(lifecycleAudit)
    }

    private fun intent(cancelRemaining: Boolean) = ExchangeEndingIntent().apply {
        exchangeId = exchange.id
        workflowInstanceId = instanceId
        cancelRemainingInformationRequests = cancelRemaining
        requestedByPrincipalKind = PrincipalKind.USER
        requestedByPrincipalId = requesterId
    }

    private fun approval(principalId: UUID) =
        WorkflowDecisionRecord(PrincipalRef.user(principalId), Decision.APPROVE, Instant.now())

    private fun confirmation() = DomainEvent(
        type = ExchangeApprovalEventHandler.EVENT_ENDED_CONFIRMED,
        subject = DomainEvent.SubjectRef(ResourceType.EXCHANGE.name, exchange.id.toString()),
        payload = mapOf("instanceId" to instanceId.toString()),
    )
}
