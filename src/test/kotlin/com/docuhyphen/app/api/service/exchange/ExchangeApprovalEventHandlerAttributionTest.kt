package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.InformationRequestAuditActor
import com.docuhyphen.app.api.model.workflow.WorkflowDecisionRecord
import com.docuhyphen.app.api.repository.exchange.ExchangeRepository
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.informationrequest.parent.InformationRequestParentLifecycleService
import com.docuhyphen.app.api.model.notification.DomainEvent
import com.docuhyphen.app.api.service.organization.OrganizationService
import com.docuhyphen.app.api.service.workflow.Decision
import com.docuhyphen.app.api.service.workflow.WorkflowEngineService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.*

class ExchangeApprovalEventHandlerAttributionTest {
    private val exchange = Exchange().apply {
        id = UUID.randomUUID()
        ownerOrganizationId = UUID.randomUUID()
        status = ExchangeStatus.INITIATED
        isDeleted = false
    }
    private val instanceId = UUID.randomUUID()
    private val approverId = UUID.randomUUID()
    private val approver = InformationRequestAuditActor.system(PrincipalRef.user(approverId))
    private val system = InformationRequestAuditActor.system(PrincipalRef(PrincipalKind.SERVICE_ACCOUNT, UUID(0, 0)))
    private val exchangeRepository = mock<ExchangeRepository>()
    private val requestParentLifecycle = mock<InformationRequestParentLifecycleService>()
    private val workflowEngine = mock<WorkflowEngineService>()
    private val organizationService = mock<OrganizationService>()
    private val handler = ExchangeApprovalEventHandler(
        shareService = mock(),
        exchangeRepository = exchangeRepository,
        workflowEngineService = workflowEngine,
        organizationService = organizationService,
        exchangeInitiationService = mock(),
        exchangeParticipantOrgService = mock(),
        lifecycleNotificationService = mock(),
        exchangeRecipientService = mock(),
        requestParentLifecycle = requestParentLifecycle,
        requestCompletion = mock(),
        endingIntents = mock(),
        lifecycleAudit = mock(),
    )

    init {
        whenever(exchangeRepository.findByIdForUpdate(exchange.id)).thenReturn(exchange)
    }

    @Test
    fun `an approval workflow rejection changes the requests in the name of the approver who rejected`() {
        whenever(workflowEngine.decisionsOf(instanceId)).thenReturn(
            listOf(
                decision(UUID.randomUUID(), Decision.APPROVE, Instant.now().minusSeconds(60)),
                decision(approverId, Decision.REJECT, Instant.now()),
            ),
        )

        handler.handle(event(ExchangeApprovalEventHandler.EVENT_REJECTED))

        verify(requestParentLifecycle).apply(exchange.id, ExchangeStatus.REJECTED, false, approver)
    }

    @Test
    fun `an approval workflow activation changes the requests in the name of the approver who approved`() {
        whenever(workflowEngine.decisionsOf(instanceId))
            .thenReturn(listOf(decision(approverId, Decision.APPROVE, Instant.now())))

        handler.handle(event(ExchangeApprovalEventHandler.EVENT_ACTIVATED))

        verify(requestParentLifecycle).apply(
            exchange.id,
            ExchangeStatus.ACCEPTED_STARTED,
            false,
            approver,
            ExchangeStatus.INITIATED,
        )
    }

    @Test
    fun `a draft approval that starts the Exchange without recipient acceptance moves its requests on from the draft`() {
        whenever(organizationService.getOrganizationById(exchange.ownerOrganizationId!!)).thenReturn(
            Organization().apply { settings = OrganizationSettings().apply { requireRecipientAcceptance = false } },
        )
        whenever(workflowEngine.decisionsOf(instanceId))
            .thenReturn(listOf(decision(approverId, Decision.APPROVE, Instant.now())))

        handler.handle(event(ExchangeApprovalEventHandler.EVENT_DRAFT_APPROVED))

        verify(requestParentLifecycle).apply(
            exchange.id,
            ExchangeStatus.ACCEPTED_STARTED,
            false,
            approver,
            ExchangeStatus.INITIATED,
        )
    }

    @Test
    fun `an outcome no person decided keeps the system attribution`() {
        handler.handle(event(ExchangeApprovalEventHandler.EVENT_REJECTED))

        verify(requestParentLifecycle).apply(exchange.id, ExchangeStatus.REJECTED, false, system)
    }

    private fun decision(principalId: UUID, decision: Decision, at: Instant) =
        WorkflowDecisionRecord(PrincipalRef.user(principalId), decision, at)

    private fun event(type: String) = DomainEvent(
        type = type,
        subject = DomainEvent.SubjectRef(ResourceType.EXCHANGE.name, exchange.id.toString()),
        payload = mapOf("instanceId" to instanceId.toString()),
    )
}
