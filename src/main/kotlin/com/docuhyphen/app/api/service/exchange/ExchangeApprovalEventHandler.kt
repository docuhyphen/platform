package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.model.entity.ExchangeStatus
import com.docuhyphen.app.api.repository.ExchangeRepository
import com.docuhyphen.app.api.service.notification.DomainEvent
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Applies the side-effects of the group-session approval workflow to the [com.docuhyphen.app.api.model.entity.Share]
 * model. The approval workflow ([DefaultWorkflowEngineService]) emits `session.activated` /
 * `session.rejected` domain events when its single APPROVAL step resolves (via an assignee
 * decision *or* via SLA escalation auto-approve/auto-reject). This handler is the business sink
 * for those events, distinct from the notification fan-out, so the outcome is applied uniformly
 * regardless of which path produced it.
 *
 * Invoked synchronously by [com.docuhyphen.app.api.service.notification.EventRouter], inside the
 * same transaction as the decision, so the share state and the workflow state commit atomically.
 */
@ApplicationScoped
class ExchangeApprovalEventHandler @Inject constructor(
    private val shareService: ShareService,
    private val exchangeRepository: ExchangeRepository,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(ExchangeApprovalEventHandler::class.java)
        const val EVENT_ACTIVATED = "session.activated"
        const val EVENT_REJECTED = "session.rejected"
    }

    /** True for the event types this handler acts on, lets the router skip the lookup otherwise. */
    fun handles(eventType: String): Boolean =
        eventType == EVENT_ACTIVATED || eventType == EVENT_REJECTED

    @Transactional
    fun handle(event: DomainEvent)
    {
        if (!handles(event.type)) return
        val subject = event.subject ?: return
        if (subject.type != ResourceType.EXCHANGE.name) return
        val exchangeId = runCatching { UUID.fromString(subject.id) }.getOrNull() ?: run {
            logger.warn("Approval event {} has unparseable session id '{}'", event.type, subject.id)
            return
        }

        when (event.type)
        {
            EVENT_ACTIVATED ->
            {
                val activated = shareService.activatePendingForResource(ResourceType.EXCHANGE, exchangeId)
                logger.info("Session {} approved: activated {} pending share(s)", exchangeId, activated)
            }

            EVENT_REJECTED ->
            {
                shareService.revokePendingForResource(ResourceType.EXCHANGE, exchangeId)
                exchangeRepository.findById(exchangeId)?.let { session ->
                    session.status = ExchangeStatus.REJECTED
                    exchangeRepository.update(session)
                }
                logger.info("Session {} rejected by approval workflow: pending shares revoked", exchangeId)
            }
        }
    }
}
