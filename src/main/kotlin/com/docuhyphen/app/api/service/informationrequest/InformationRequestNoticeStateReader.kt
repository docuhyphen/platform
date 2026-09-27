package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequestNoticeAttemptOutcome
import com.docuhyphen.app.api.model.entity.InformationRequestNoticeDeliveryAttempt
import com.docuhyphen.app.api.model.entity.InformationRequestNoticeDeliveryState
import com.docuhyphen.app.api.model.entity.InformationRequestNoticeEndpointState
import com.docuhyphen.app.api.model.entity.InformationRequestOutboundNotice
import com.docuhyphen.app.api.model.informationrequest.InformationRequestNoticeView
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestNoticeClaimRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestNoticeDeliveryAttemptRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestNoticeIntentRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestOutboundNoticeRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.UUID

@ApplicationScoped
class InformationRequestNoticeStateReader @Inject constructor(
    private val intentRepository: InformationRequestNoticeIntentRepository,
    private val claimRepository: InformationRequestNoticeClaimRepository,
    private val noticeRepository: InformationRequestOutboundNoticeRepository,
    private val attemptRepository: InformationRequestNoticeDeliveryAttemptRepository,
)
{
    fun views(requestId: UUID): List<InformationRequestNoticeView> = viewsFor(listOf(requestId))

    fun viewsFor(requestIds: Collection<UUID>): List<InformationRequestNoticeView>
    {
        if (requestIds.isEmpty()) return emptyList()
        val claimed = claimRepository.findForRequests(requestIds).map { it.noticeIntentId }.toSet()
        val notices = noticeRepository.findForRequests(requestIds).associateBy { it.noticeIntentId }
        val attempts = attemptRepository.findForRequests(requestIds).groupBy { it.outboundNoticeId }
        return intentRepository.findForRequests(requestIds).map { intent ->
            val notice = notices[intent.id]
            val noticeAttempts = notice?.let { attempts[it.id] }.orEmpty().sortedBy { it.attemptNumber }
            InformationRequestNoticeView(intent, notice, noticeAttempts, stateOf(intent.id in claimed, notice, noticeAttempts))
        }
    }

    fun states(requestId: UUID): Map<UUID, InformationRequestNoticeDeliveryState> =
        views(requestId).associate { it.intent.id to it.deliveryState }

    fun statesForRequests(requestIds: Collection<UUID>): Map<UUID, List<InformationRequestNoticeDeliveryState>> =
        viewsFor(requestIds).groupBy({ it.intent.informationRequestId }, { it.deliveryState })

    fun stateOf(
        claimed: Boolean,
        notice: InformationRequestOutboundNotice?,
        attempts: List<InformationRequestNoticeDeliveryAttempt>,
    ): InformationRequestNoticeDeliveryState = when
    {
        !claimed -> InformationRequestNoticeDeliveryState.PENDING
        notice == null -> InformationRequestNoticeDeliveryState.CLAIMED
        notice.endpointState == InformationRequestNoticeEndpointState.MISSING -> InformationRequestNoticeDeliveryState.UNDELIVERABLE
        attempts.any { it.outcome == InformationRequestNoticeAttemptOutcome.DELIVERED } -> InformationRequestNoticeDeliveryState.DELIVERED
        attempts.any { it.outcome == InformationRequestNoticeAttemptOutcome.SKIPPED } -> InformationRequestNoticeDeliveryState.UNDELIVERABLE
        attempts.isEmpty() -> InformationRequestNoticeDeliveryState.RENDERED
        attempts.size >= MAXIMUM_ATTEMPTS -> InformationRequestNoticeDeliveryState.FAILED
        else -> InformationRequestNoticeDeliveryState.RETRYING
    }

    companion object
    {
        const val MAXIMUM_ATTEMPTS = 5
    }
}
