package com.docuhyphen.app.api.service.sharingsession

import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.model.entity.SharingSessionStatus
import com.docuhyphen.app.api.repository.SharingSessionRepository
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
 * for those events — distinct from the notification fan-out — so the outcome is applied uniformly
 * regardless of which path produced it.
 *
 * Invoked synchronously by [com.docuhyphen.app.api.service.notification.EventRouter], inside the
 * same transaction as the decision, so the share state and the workflow state commit atomically.
 */
@ApplicationScoped
class SessionApprovalEventHandler @Inject constructor(
    private val shareService: ShareService,
    private val sharingSessionRepository: SharingSessionRepository,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(SessionApprovalEventHandler::class.java)
        const val EVENT_ACTIVATED = "session.activated"
        const val EVENT_REJECTED = "session.rejected"
    }

    /** True for the event types this handler acts on — lets the router skip the lookup otherwise. */
    fun handles(eventType: String): Boolean =
        eventType == EVENT_ACTIVATED || eventType == EVENT_REJECTED

    @Transactional
    fun handle(event: DomainEvent)
    {
        if (!handles(event.type)) return
        val subject = event.subject ?: return
        if (subject.type != ResourceType.SHARING_SESSION.name) return
        val sessionId = runCatching { UUID.fromString(subject.id) }.getOrNull() ?: run {
            logger.warn("Approval event {} has unparseable session id '{}'", event.type, subject.id)
            return
        }

        when (event.type)
        {
            EVENT_ACTIVATED ->
            {
                val activated = shareService.activatePendingForResource(ResourceType.SHARING_SESSION, sessionId)
                logger.info("Session {} approved: activated {} pending share(s)", sessionId, activated)
            }

            EVENT_REJECTED ->
            {
                shareService.revokePendingForResource(ResourceType.SHARING_SESSION, sessionId)
                sharingSessionRepository.findById(sessionId)?.let { session ->
                    session.status = SharingSessionStatus.REJECTED
                    sharingSessionRepository.update(session)
                }
                logger.info("Session {} rejected by approval workflow: pending shares revoked", sessionId)
            }
        }
    }
}
