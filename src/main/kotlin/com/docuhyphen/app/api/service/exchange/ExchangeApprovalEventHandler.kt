package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.exchange.ExchangeLifecycleActor
import com.docuhyphen.app.api.model.informationrequest.InformationRequestAuditActor
import com.docuhyphen.app.api.model.workflow.TriggerRequest
import com.docuhyphen.app.api.repository.exchange.ExchangeRepository
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.informationrequest.parent.InformationRequestExchangeCompletionException
import com.docuhyphen.app.api.service.informationrequest.parent.InformationRequestExchangeCompletionService
import com.docuhyphen.app.api.service.informationrequest.parent.InformationRequestParentLifecycleService
import com.docuhyphen.app.api.service.notification.DomainEvent
import com.docuhyphen.app.api.service.organization.OrganizationService
import com.docuhyphen.app.api.service.workflow.Decision
import com.docuhyphen.app.api.service.workflow.WorkflowEngineService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import java.sql.Timestamp
import java.time.Instant
import java.util.*

@ApplicationScoped
class ExchangeApprovalEventHandler @Inject constructor(
    private val shareService: ShareService,
    private val exchangeRepository: ExchangeRepository,
    private val workflowEngineService: WorkflowEngineService,
    private val organizationService: OrganizationService,
    private val exchangeInitiationService: ExchangeInitiationService,
    private val exchangeParticipantOrgService: ExchangeParticipantOrgService,
    private val lifecycleNotificationService: ExchangeLifecycleNotificationService,
    private val exchangeRecipientService: ExchangeRecipientService,
    private val requestParentLifecycle: InformationRequestParentLifecycleService,
    private val requestCompletion: InformationRequestExchangeCompletionService,
    private val endingIntents: ExchangeEndingIntentService,
    private val lifecycleAudit: ExchangeLifecycleAudit,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(ExchangeApprovalEventHandler::class.java)

        const val EVENT_ACTIVATED = "session.activated"
        const val EVENT_REJECTED = "session.rejected"

        const val EVENT_EXCHANGE_ACTIVATED = "exchange.activated"
        const val EVENT_DRAFT_APPROVED = "exchange.draft_approved"
        const val EVENT_ENDING = "exchange.ending"
        const val EVENT_ENDED_CONFIRMED = "exchange.ended_confirmed"

        const val EVENT_RECEIVED = "exchange.received"
        const val EVENT_RECEIVED_ACTIVATED = "exchange.received_activated"
        const val EVENT_RECEIVED_ENDING = "exchange.received_ending"

        private const val INSTANCE_ID = "instanceId"
        private val SYSTEM_PRINCIPAL = PrincipalRef(PrincipalKind.SERVICE_ACCOUNT, UUID(0, 0))
    }

    fun handles(eventType: String): Boolean =
        eventType == EVENT_ACTIVATED ||
                eventType == EVENT_REJECTED ||
                eventType == EVENT_EXCHANGE_ACTIVATED ||
                eventType == EVENT_DRAFT_APPROVED ||
                eventType == EVENT_ENDING ||
                eventType == EVENT_ENDED_CONFIRMED

    @Transactional
    fun handle(event: DomainEvent)
    {
        if (!handles(event.type)) return
        val subject = event.subject ?: return
        if (subject.type != ResourceType.EXCHANGE.name) return
        val exchangeId = runCatching { UUID.fromString(subject.id) }.getOrNull() ?: run {
            logger.warn("Lifecycle event {} has unparseable Exchange id '{}'", event.type, subject.id)
            return
        }

        val parent = exchangeRepository.findByIdForUpdate(exchangeId) ?: return
        if (parent.isDeleted || parent.status in setOf(
                ExchangeStatus.REJECTED,
                ExchangeStatus.RESCINDED,
                ExchangeStatus.ENDED
            )
        ) return

        when (event.type)
        {
            EVENT_ACTIVATED ->
            {
                val activated = activateExchangeGateShares(exchangeId)
                exchangeRepository.findByIdForUpdate(exchangeId)?.let { session ->
                    if (session.status != ExchangeStatus.ACCEPTED_STARTED)
                    {
                        val previousStatus = session.status
                        session.status = ExchangeStatus.ACCEPTED_STARTED
                        exchangeRepository.update(session)
                        recordTransition(session, previousStatus)
                        requestParentLifecycle.apply(
                            exchangeId, session.status, session.isDeleted,
                            outcomeActor(event, Decision.APPROVE),
                            previousStatus,
                        )
                        lifecycleNotificationService.publish(session, ExchangeStatus.ACCEPTED_STARTED)
                    }
                }
                exchangeInitiationService.notifyRecipientOnActivation(exchangeId)
                logger.info(
                    "Exchange {} approved (legacy): activated {} pending share(s), status -> ACCEPTED_STARTED",
                    exchangeId, activated,
                )
            }

            EVENT_REJECTED ->
            {
                exchangeRepository.findByIdForUpdate(exchangeId)?.let { session ->
                    if (session.status != ExchangeStatus.REJECTED)
                    {
                        val previousStatus = session.status
                        session.status = ExchangeStatus.REJECTED
                        session.endDate = Timestamp.from(Instant.now())
                        exchangeRepository.update(session)
                        recordTransition(session, previousStatus)
                        requestParentLifecycle.apply(
                            exchangeId, session.status, session.isDeleted,
                            outcomeActor(event, Decision.REJECT),
                        )
                        lifecycleNotificationService.publish(session, ExchangeStatus.REJECTED)
                    }
                }
                shareService.revokePendingForResource(ResourceType.EXCHANGE, exchangeId)
                logger.info("Exchange {} rejected by approval workflow: pending shares revoked", exchangeId)
            }

            EVENT_EXCHANGE_ACTIVATED ->
            {
                val activated = activateExchangeGateShares(exchangeId)
                val exchange = exchangeRepository.findByIdForUpdate(exchangeId)
                val orgId = exchange?.ownerOrganizationId
                val requireRecipientAcceptance = orgId
                    ?.let { organizationService.getOrganizationById(it) }?.settings?.requireRecipientAcceptance
                    ?: true

                exchange?.let { session ->
                    if (session.status != ExchangeStatus.ACCEPTED_STARTED)
                    {
                        if (!requireRecipientAcceptance)
                        {
                            val previousStatus = session.status
                            session.status = ExchangeStatus.ACCEPTED_STARTED
                            exchangeRepository.update(session)
                            recordTransition(session, previousStatus)
                            requestParentLifecycle.apply(
                                exchangeId, session.status, session.isDeleted,
                                outcomeActor(event, Decision.APPROVE),
                                previousStatus,
                            )
                            lifecycleNotificationService.publish(session, ExchangeStatus.ACCEPTED_STARTED)
                            logger.info(
                                "Exchange {} activated: {} pending share(s) activated, status -> ACCEPTED_STARTED",
                                exchangeId, activated,
                            )
                        }
                        else
                        {
                            logger.info(
                                "Exchange {} activated: {} pending share(s) activated; requireRecipientAcceptance=true, keeping INITIATED for recipient dialog",
                                exchangeId, activated,
                            )
                        }
                    }
                    else
                    {
                        logger.info(
                            "Exchange {} activated: {} pending share(s) activated; already ACCEPTED_STARTED",
                            exchangeId, activated,
                        )
                    }
                }
                exchangeInitiationService.notifyRecipientOnActivation(exchangeId)
                val subjectData = buildMap<String, String> {
                    exchange?.initiator?.id?.let { put("initiatorId", it.toString()) }
                    orgId?.let { put("orgId", it.toString()) }
                }
                fireRecipientTriggers(exchangeId, EVENT_RECEIVED_ACTIVATED, subjectData, exchange?.initiator?.id)
            }

            EVENT_DRAFT_APPROVED ->
            {
                logger.info("Exchange {} draft approved; checking whether to fire acceptance_pending", exchangeId)
                val exchange = exchangeRepository.findByIdForUpdate(exchangeId) ?: run {
                    logger.warn("Exchange {} not found for draft_approved event", exchangeId)
                    return
                }
                val orgId = exchange.ownerOrganizationId
                val settings = orgId?.let { organizationService.getOrganizationById(it) }?.settings
                val requireAcceptance = settings?.requireRecipientAcceptance ?: true

                val subjectData = buildMap<String, String> {
                    exchange.initiator?.id?.let { put("initiatorId", it.toString()) }
                    orgId?.let { put("orgId", it.toString()) }
                }

                if (requireAcceptance)
                {
                    workflowEngineService.trigger(
                        TriggerRequest(
                            triggerEvent = "exchange.acceptance_pending",
                            subjectResourceType = ResourceType.EXCHANGE.name,
                            subjectResourceId = exchangeId,
                            organizationId = orgId,
                            subjectData = subjectData,
                            initiatedByAppUserId = exchange.initiator?.id,
                        )
                    )
                    logger.info("Exchange {}: fired acceptance_pending after draft_approved", exchangeId)
                    fireRecipientTriggers(exchangeId, EVENT_RECEIVED, subjectData, exchange.initiator?.id)
                }
                else
                {
                    val previousStatus = exchange.status
                    exchange.status = ExchangeStatus.ACCEPTED_STARTED
                    exchangeRepository.update(exchange)
                    recordTransition(exchange, previousStatus)
                    requestParentLifecycle.apply(
                        exchangeId, exchange.status, exchange.isDeleted,
                        outcomeActor(event, Decision.APPROVE),
                        previousStatus,
                    )
                    lifecycleNotificationService.publish(exchange, ExchangeStatus.ACCEPTED_STARTED)
                    workflowEngineService.trigger(
                        TriggerRequest(
                            triggerEvent = "exchange.activated",
                            subjectResourceType = ResourceType.EXCHANGE.name,
                            subjectResourceId = exchangeId,
                            organizationId = orgId,
                            subjectData = subjectData,
                            initiatedByAppUserId = exchange.initiator?.id,
                        )
                    )
                    logger.info("Exchange {}: auto-advanced to ACCEPTED_STARTED after draft_approved", exchangeId)
                    fireRecipientTriggers(exchangeId, EVENT_RECEIVED_ACTIVATED, subjectData, exchange.initiator?.id)
                }
            }

            EVENT_ENDING ->
            {
                val exchange = exchangeRepository.findByIdForUpdate(exchangeId)
                val intent = endingIntents.pendingFor(exchangeId, workflowInstanceOf(event))
                val actor = endingActor(event, intent)
                if (exchange != null && !requestsPermitEnding(exchange, intent, actor, event)) return
                exchange?.let { session ->
                    if (session.status != ExchangeStatus.ENDED)
                    {
                        val previousStatus = session.status
                        session.status = ExchangeStatus.ENDED
                        session.endDate = Timestamp.from(Instant.now())
                        exchangeRepository.update(session)
                        recordTransition(session, previousStatus)
                        requestParentLifecycle.apply(
                            exchangeId, session.status, session.isDeleted,
                            actor,
                        )
                        lifecycleNotificationService.publish(session, ExchangeStatus.ENDED)
                    }
                }
                intent?.let(endingIntents::consume)
                logger.info("Exchange {} ended (via exchange.ending event from workflow step)", exchangeId)
                val orgId = exchange?.ownerOrganizationId
                val subjectData = buildMap<String, String> {
                    exchange?.initiator?.id?.let { put("initiatorId", it.toString()) }
                    orgId?.let { put("orgId", it.toString()) }
                }
                fireRecipientTriggers(exchangeId, EVENT_RECEIVED_ENDING, subjectData, exchange?.initiator?.id)
            }

            EVENT_ENDED_CONFIRMED ->
            {
                val exchange = exchangeRepository.findByIdForUpdate(exchangeId)
                val intent = endingIntents.pendingFor(exchangeId, workflowInstanceOf(event))
                val actor = endingActor(event, intent)
                if (exchange != null && !requestsPermitEnding(exchange, intent, actor, event)) return
                exchange?.let { session ->
                    if (session.status != ExchangeStatus.ENDED)
                    {
                        val previousStatus = session.status
                        session.status = ExchangeStatus.ENDED
                        session.endDate = Timestamp.from(Instant.now())
                        exchangeRepository.update(session)
                        recordTransition(session, previousStatus)
                        requestParentLifecycle.apply(
                            exchangeId, session.status, session.isDeleted,
                            actor,
                        )
                        lifecycleNotificationService.publish(session, ExchangeStatus.ENDED)
                    }
                }
                intent?.let(endingIntents::consume)
                logger.info("Exchange {} ended (confirmed by workflow outcome)", exchangeId)
                val orgId = exchange?.ownerOrganizationId
                val subjectData = buildMap<String, String> {
                    exchange?.initiator?.id?.let { put("initiatorId", it.toString()) }
                    orgId?.let { put("orgId", it.toString()) }
                }
                fireRecipientTriggers(exchangeId, EVENT_RECEIVED_ENDING, subjectData, exchange?.initiator?.id)
            }
        }
    }

    private fun requestsPermitEnding(
        exchange: Exchange,
        intent: ExchangeEndingIntent?,
        actor: InformationRequestAuditActor,
        event: DomainEvent,
    ): Boolean =
        try
        {
            requestCompletion.prepareEnding(
                exchange,
                actor,
                intent?.cancelRemainingInformationRequests ?: false,
                cancelAuthorityCheckedAt = intent?.createdAt?.toInstant(),
            )
            true
        }
        catch (refusal: InformationRequestExchangeCompletionException)
        {
            logger.warn(
                "Exchange {} was not ended because its Information Requests refused ending: {} {}",
                exchange.id, refusal.reasonCode, refusal.requestIds,
            )
            recordEndingRefused(exchange, intent, refusal, workflowInstanceOf(event))
            false
        }

    private fun recordEndingRefused(
        exchange: Exchange,
        intent: ExchangeEndingIntent?,
        refusal: InformationRequestExchangeCompletionException,
        workflowInstanceId: UUID?,
    ) {
        lifecycleAudit.recordEndingRefused(
            exchange.id,
            exchange.name,
            refusal.reasonCode,
            refusal.requestIds.size,
            workflowInstanceId,
        )
        val requester = intent
            ?.takeIf { it.requestedByPrincipalKind == PrincipalKind.USER }
            ?.requestedByPrincipalId
        val approvers = workflowInstanceId?.let(workflowEngineService::decisionsOf).orEmpty()
            .filter { it.decision == Decision.APPROVE && it.principal.kind == PrincipalKind.USER }
            .map { it.principal.id }
        lifecycleNotificationService.publishEndingRefused(exchange, (listOfNotNull(requester) + approvers).toSet())
    }

    private fun endingActor(event: DomainEvent, intent: ExchangeEndingIntent?): InformationRequestAuditActor =
        InformationRequestAuditActor.system(
            intent?.let { PrincipalRef(it.requestedByPrincipalKind, it.requestedByPrincipalId) }
                ?: decidedBy(event, Decision.APPROVE)
                ?: SYSTEM_PRINCIPAL,
        )

    private fun outcomeActor(event: DomainEvent, decision: Decision): InformationRequestAuditActor =
        InformationRequestAuditActor.system(decidedBy(event, decision) ?: SYSTEM_PRINCIPAL)

    private fun decidedBy(event: DomainEvent, decision: Decision): PrincipalRef? =
        workflowInstanceOf(event)
            ?.let(workflowEngineService::decisionsOf)
            .orEmpty()
            .lastOrNull { it.decision == decision }
            ?.principal

    private fun recordTransition(exchange: Exchange, previousStatus: ExchangeStatus) =
        lifecycleAudit.recordTransition(
            exchange.id,
            exchange.name,
            previousStatus,
            exchange.status,
            ExchangeLifecycleActor.WORKFLOW,
        )

    private fun workflowInstanceOf(event: DomainEvent): UUID? =
        event.payload[INSTANCE_ID]?.let { runCatching { UUID.fromString(it) }.getOrNull() }

    private fun activateExchangeGateShares(exchangeId: UUID): Int =
        shareService.activatePendingForResource(
            ResourceType.EXCHANGE,
            exchangeId,
            exchangeRecipientService.pendingTrustedParticipantShareIds(exchangeId),
        )

    private fun fireRecipientTriggers(
        exchangeId: UUID,
        recipientEvent: String,
        baseSubjectData: Map<String, String>,
        initiatedByAppUserId: UUID?,
    )
    {
        val recipientOrgIds = runCatching { exchangeParticipantOrgService.findRecipientOrgIds(exchangeId) }
            .getOrElse { e ->
                logger.warn("Could not resolve recipient orgs for Exchange {}: {}", exchangeId, e.message)
                emptyList()
            }
        for (recipientOrgId in recipientOrgIds)
        {
            val subjectData = baseSubjectData + mapOf("recipientOrgId" to recipientOrgId.toString())
            workflowEngineService.trigger(
                TriggerRequest(
                    triggerEvent = recipientEvent,
                    subjectResourceType = ResourceType.EXCHANGE.name,
                    subjectResourceId = exchangeId,
                    organizationId = recipientOrgId,
                    subjectData = subjectData,
                    initiatedByAppUserId = initiatedByAppUserId,
                )
            )
            logger.info("Exchange {}: fired {} for recipient org {}", exchangeId, recipientEvent, recipientOrgId)
        }
    }
}
