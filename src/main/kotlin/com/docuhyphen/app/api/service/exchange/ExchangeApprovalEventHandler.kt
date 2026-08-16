package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.model.entity.ExchangeStatus
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.repository.exchange.ExchangeRepository
import com.docuhyphen.app.api.service.notification.DomainEvent
import com.docuhyphen.app.api.service.organization.OrganizationService
import com.docuhyphen.app.api.service.workflow.TriggerRequest
import com.docuhyphen.app.api.service.workflow.WorkflowEngineService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/**
 * Applies the side-effects of workflow lifecycle events to the Exchange and Share models.
 *
 * Legacy events: `session.activated` / `session.rejected` (group-approval workflow).
 * New lifecycle events: `exchange.activated`, `exchange.draft_approved`,
 * `exchange.ending`, `exchange.ended_confirmed`.
 *
 * Invoked by [com.docuhyphen.app.api.service.notification.EventRouter]. For workflow-originated
 * lifecycle events this runs from the transactional event outbox dispatcher, after the workflow
 * decision has committed, not in the decision's own transaction. Delivery is at-least-once and may
 * be retried after an ambiguous failure, so this handler is idempotent by business key: every
 * mutation is guarded on the Exchange's current status, so re-applying a completed transition is a
 * no-op rather than a repeated side-effect.
 */
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
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(ExchangeApprovalEventHandler::class.java)

        // Legacy group-approval events
        const val EVENT_ACTIVATED = "session.activated"
        const val EVENT_REJECTED = "session.rejected"

        // New lifecycle events
        const val EVENT_EXCHANGE_ACTIVATED = "exchange.activated"
        const val EVENT_DRAFT_APPROVED = "exchange.draft_approved"
        const val EVENT_ENDING = "exchange.ending"
        const val EVENT_ENDED_CONFIRMED = "exchange.ended_confirmed"

        // Recipient-side lifecycle events
        const val EVENT_RECEIVED = "exchange.received"
        const val EVENT_RECEIVED_ACTIVATED = "exchange.received_activated"
        const val EVENT_RECEIVED_ENDING = "exchange.received_ending"
    }

    /** True for the event types this handler acts on. */
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
            logger.warn("Lifecycle event {} has unparseable exchange id '{}'", event.type, subject.id)
            return
        }

        when (event.type)
        {
            // ---------------------------------------------------------------------------
            // Legacy group-approval path
            // ---------------------------------------------------------------------------

            EVENT_ACTIVATED ->
            {
                val activated = activateExchangeGateShares(exchangeId)
                // Fix known gap: the legacy handler did not set exchange.status; do it now.
                exchangeRepository.findById(exchangeId)?.let { session ->
                    if (session.status != ExchangeStatus.ACCEPTED_STARTED)
                    {
                        session.status = ExchangeStatus.ACCEPTED_STARTED
                        exchangeRepository.update(session)
                        lifecycleNotificationService.publish(session, ExchangeStatus.ACCEPTED_STARTED)
                    }
                }
                // Shares are now ACTIVE; send the invite email that was deferred during approval.
                exchangeInitiationService.notifyRecipientOnActivation(exchangeId)
                logger.info(
                    "Exchange {} approved (legacy): activated {} pending share(s), status -> ACCEPTED_STARTED",
                    exchangeId, activated,
                )
            }

            EVENT_REJECTED ->
            {
                exchangeRepository.findById(exchangeId)?.let { session ->
                    if (session.status != ExchangeStatus.REJECTED)
                    {
                        session.status = ExchangeStatus.REJECTED
                        session.endDate = Timestamp.from(Instant.now())
                        exchangeRepository.update(session)
                        lifecycleNotificationService.publish(session, ExchangeStatus.REJECTED)
                    }
                }
                shareService.revokePendingForResource(ResourceType.EXCHANGE, exchangeId)
                logger.info("Exchange {} rejected by approval workflow: pending shares revoked", exchangeId)
            }

            // ---------------------------------------------------------------------------
            // New lifecycle events
            // ---------------------------------------------------------------------------

            EVENT_EXCHANGE_ACTIVATED ->
            {
                // Fired when the acceptance_pending workflow completes (or auto-acceptance).
                val activated = activateExchangeGateShares(exchangeId)
                val exchange = exchangeRepository.findById(exchangeId)
                val orgId = exchange?.ownerOrganizationId
                val requireRecipientAcceptance = orgId
                    ?.let { organizationService.getOrganizationById(it) }?.settings?.requireRecipientAcceptance
                    ?: true

                exchange?.let { session ->
                    if (session.status != ExchangeStatus.ACCEPTED_STARTED)
                    {
                        if (!requireRecipientAcceptance)
                        {
                            // Acceptance is not required: auto-advance straight to active.
                            session.status = ExchangeStatus.ACCEPTED_STARTED
                            exchangeRepository.update(session)
                            lifecycleNotificationService.publish(session, ExchangeStatus.ACCEPTED_STARTED)
                            logger.info(
                                "Exchange {} activated: {} pending share(s) activated, status -> ACCEPTED_STARTED",
                                exchangeId, activated,
                            )
                        }
                        else
                        {
                            // Acceptance is required: shares are now ACTIVE so the recipient can load
                            // the exchange by direct URL, but status stays INITIATED so the acceptance
                            // dialog is presented. The recipient's explicit accept/reject advances status.
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
                // Send the invite email now that the recipient's share is ACTIVE.
                exchangeInitiationService.notifyRecipientOnActivation(exchangeId)
                val subjectData = buildMap<String, String> {
                    exchange?.initiator?.id?.let { put("initiatorId", it.toString()) }
                    orgId?.let { put("orgId", it.toString()) }
                }
                fireRecipientTriggers(exchangeId, EVENT_RECEIVED_ACTIVATED, subjectData, exchange?.initiator?.id)
            }

            EVENT_DRAFT_APPROVED ->
            {
                // A pre-send (draft_submitted) approval workflow completed successfully.
                // Fire exchange.acceptance_pending or auto-advance to ACCEPTED_STARTED,
                // depending on the org's requireRecipientAcceptance setting.
                logger.info("Exchange {} draft approved; checking whether to fire acceptance_pending", exchangeId)
                val exchange = exchangeRepository.findById(exchangeId) ?: run {
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
                    // Fire exchange.received in each recipient org's context
                    fireRecipientTriggers(exchangeId, EVENT_RECEIVED, subjectData, exchange.initiator?.id)
                }
                else
                {
                    // Auto-advance: no acceptance required
                    exchange.status = ExchangeStatus.ACCEPTED_STARTED
                    exchangeRepository.update(exchange)
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

            // exchange.ending is emitted by a workflow step to signal the ending workflow completed.
            EVENT_ENDING ->
            {
                val exchange = exchangeRepository.findById(exchangeId)
                exchange?.let { session ->
                    if (session.status != ExchangeStatus.ENDED)
                    {
                        session.status = ExchangeStatus.ENDED
                        session.endDate = Timestamp.from(Instant.now())
                        exchangeRepository.update(session)
                        lifecycleNotificationService.publish(session, ExchangeStatus.ENDED)
                    }
                }
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
                // Canonical terminal event from an ending workflow (step onApprove.emit).
                val exchange = exchangeRepository.findById(exchangeId)
                exchange?.let { session ->
                    if (session.status != ExchangeStatus.ENDED)
                    {
                        session.status = ExchangeStatus.ENDED
                        session.endDate = Timestamp.from(Instant.now())
                        exchangeRepository.update(session)
                        lifecycleNotificationService.publish(session, ExchangeStatus.ENDED)
                    }
                }
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

    private fun activateExchangeGateShares(exchangeId: UUID): Int =
        shareService.activatePendingForResource(
            ResourceType.EXCHANGE,
            exchangeId,
            exchangeRecipientService.pendingTrustedParticipantShareIds(exchangeId),
        )

    /**
     * Fires [recipientEvent] in the context of each recipient org for [exchangeId].
     * Adds `recipientOrgId` to the subject data so recipient workflow definitions can reference it.
     */
    private fun fireRecipientTriggers(
        exchangeId: UUID,
        recipientEvent: String,
        baseSubjectData: Map<String, String>,
        initiatedByAppUserId: UUID?,
    )
    {
        val recipientOrgIds = runCatching { exchangeParticipantOrgService.findRecipientOrgIds(exchangeId) }
            .getOrElse { e ->
                logger.warn("Could not resolve recipient orgs for exchange {}: {}", exchangeId, e.message)
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
