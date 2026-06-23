package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.model.entity.ExchangeStatus
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.repository.ExchangeRepository
import com.docuhyphen.app.api.repository.OrganizationRepository
import com.docuhyphen.app.api.service.notification.DomainEvent
import com.docuhyphen.app.api.service.organization.OrganizationMembershipService
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
 * Invoked synchronously by [com.docuhyphen.app.api.service.notification.EventRouter], inside the
 * same transaction as the decision, so share state and workflow state commit atomically.
 */
@ApplicationScoped
class ExchangeApprovalEventHandler @Inject constructor(
    private val shareService: ShareService,
    private val exchangeRepository: ExchangeRepository,
    private val workflowEngineService: WorkflowEngineService,
    private val organizationMembershipService: OrganizationMembershipService,
    private val organizationRepository: OrganizationRepository,
    private val exchangeInitiationService: ExchangeInitiationService,
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
                val activated = shareService.activatePendingForResource(ResourceType.EXCHANGE, exchangeId)
                // Fix known gap: the legacy handler did not set exchange.status; do it now.
                exchangeRepository.findById(exchangeId)?.let { session ->
                    if (session.status != ExchangeStatus.ACCEPTED_STARTED)
                    {
                        session.status = ExchangeStatus.ACCEPTED_STARTED
                        exchangeRepository.update(session)
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
                shareService.revokePendingForResource(ResourceType.EXCHANGE, exchangeId)
                exchangeRepository.findById(exchangeId)?.let { session ->
                    session.status = ExchangeStatus.REJECTED
                    exchangeRepository.update(session)
                }
                logger.info("Exchange {} rejected by approval workflow: pending shares revoked", exchangeId)
            }

            // ---------------------------------------------------------------------------
            // New lifecycle events
            // ---------------------------------------------------------------------------

            EVENT_EXCHANGE_ACTIVATED ->
            {
                // Fired when the acceptance_pending workflow completes (or auto-acceptance).
                val activated = shareService.activatePendingForResource(ResourceType.EXCHANGE, exchangeId)
                exchangeRepository.findById(exchangeId)?.let { session ->
                    if (session.status != ExchangeStatus.ACCEPTED_STARTED)
                    {
                        session.status = ExchangeStatus.ACCEPTED_STARTED
                        exchangeRepository.update(session)
                    }
                }
                // Shares are now ACTIVE; send the invite email that was deferred during approval.
                exchangeInitiationService.notifyRecipientOnActivation(exchangeId)
                logger.info(
                    "Exchange {} activated: activated {} pending share(s), status -> ACCEPTED_STARTED",
                    exchangeId, activated,
                )
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
                val orgId = exchange.initiator?.id?.let { organizationMembershipService.primaryOrganizationId(it) }
                val settings = orgId?.let { organizationRepository.findById(it) }?.settings
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
                }
                else
                {
                    // Auto-advance: no acceptance required
                    exchange.status = ExchangeStatus.ACCEPTED_STARTED
                    exchangeRepository.update(exchange)
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
                }
            }

            // exchange.ending is emitted by a workflow step to signal the ending workflow completed.
            EVENT_ENDING ->
            {
                exchangeRepository.findById(exchangeId)?.let { session ->
                    if (session.status != ExchangeStatus.ENDED)
                    {
                        session.status = ExchangeStatus.ENDED
                        session.endDate = Timestamp.from(Instant.now())
                        exchangeRepository.update(session)
                    }
                }
                logger.info("Exchange {} ended (via exchange.ending event from workflow step)", exchangeId)
            }

            EVENT_ENDED_CONFIRMED ->
            {
                // Canonical terminal event from an ending workflow (step onApprove.emit).
                exchangeRepository.findById(exchangeId)?.let { session ->
                    if (session.status != ExchangeStatus.ENDED)
                    {
                        session.status = ExchangeStatus.ENDED
                        session.endDate = Timestamp.from(Instant.now())
                        exchangeRepository.update(session)
                    }
                }
                logger.info("Exchange {} ended (confirmed by workflow outcome)", exchangeId)
            }
        }
    }
}
