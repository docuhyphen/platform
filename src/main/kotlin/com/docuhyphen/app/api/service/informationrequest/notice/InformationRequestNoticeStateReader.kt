package com.docuhyphen.app.api.service.informationrequest.notice

import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.notice.InformationRequestNoticeView
import com.docuhyphen.app.api.repository.informationrequest.amendment.InformationRequestNoticeIntentRepository
import com.docuhyphen.app.api.repository.informationrequest.notice.InformationRequestNoticeClaimRepository
import com.docuhyphen.app.api.repository.informationrequest.notice.InformationRequestNoticeDeliveryAttemptRepository
import com.docuhyphen.app.api.repository.informationrequest.notice.InformationRequestNoticeRenderFailureRepository
import com.docuhyphen.app.api.repository.informationrequest.notice.InformationRequestOutboundNoticeRepository
import com.docuhyphen.app.api.repository.informationrequest.party.InformationRequestPartyRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.*

@ApplicationScoped
class InformationRequestNoticeStateReader @Inject constructor(
    private val intentRepository: InformationRequestNoticeIntentRepository,
    private val claimRepository: InformationRequestNoticeClaimRepository,
    private val noticeRepository: InformationRequestOutboundNoticeRepository,
    private val attemptRepository: InformationRequestNoticeDeliveryAttemptRepository,
    private val partyRepository: InformationRequestPartyRepository,
    private val renderFailures: InformationRequestNoticeRenderFailureRepository,
)
{
    fun views(requestId: UUID): List<InformationRequestNoticeView> = viewsFor(listOf(requestId))

    fun viewsFor(requestIds: Collection<UUID>): List<InformationRequestNoticeView>
    {
        if (requestIds.isEmpty()) return emptyList()
        val claimed = claimRepository.findForRequests(requestIds).map { it.noticeIntentId }.toSet()
        val notices = noticeRepository.findForRequests(requestIds)
            .sortedWith(compareBy({ it.renderedAt }, { it.id }))
            .groupBy { it.noticeIntentId }
        val attempts = attemptRepository.findForRequests(requestIds).groupBy { it.outboundNoticeId }
        val groupParties = partyRepository.findIdsHeldByGroups(requestIds)
        val failedRenders = renderFailures.countsForRequests(requestIds)
        return intentRepository.findForRequests(requestIds).flatMap { intent ->
            val intentNotices = notices[intent.id].orEmpty()
            if (intentNotices.isEmpty()) {
                val state = unrenderedStateOf(intent.id in claimed, failedRenders[intent.id] ?: 0)
                listOf(InformationRequestNoticeView(intent, null, emptyList(), state))
            } else intentNotices.map { notice ->
                val noticeAttempts = attempts[notice.id].orEmpty().sortedBy { it.attemptNumber }
                InformationRequestNoticeView(
                    intent,
                    notice,
                    noticeAttempts,
                    stateOf(true, notice, noticeAttempts),
                    recipientWithheld = intent.partyId in groupParties,
                )
            }
        }
    }

    fun states(requestId: UUID): Map<UUID, InformationRequestNoticeDeliveryState> =
        views(requestId).groupBy { it.intent.id }.mapValues { (_, views) -> combined(views.map { it.deliveryState }) }

    private fun combined(states: List<InformationRequestNoticeDeliveryState>): InformationRequestNoticeDeliveryState =
        states.singleOrNull() ?: COMBINED_ORDER.first { it in states }

    fun statesForRequests(requestIds: Collection<UUID>): Map<UUID, List<InformationRequestNoticeDeliveryState>> =
        viewsFor(requestIds).groupBy({ it.intent.informationRequestId }, { it.deliveryState })

    private fun unrenderedStateOf(claimed: Boolean, failedRenders: Int): InformationRequestNoticeDeliveryState = when {
        claimed -> InformationRequestNoticeDeliveryState.CLAIMED
        failedRenders >= MAXIMUM_ATTEMPTS -> InformationRequestNoticeDeliveryState.FAILED
        failedRenders > 0 -> InformationRequestNoticeDeliveryState.RETRYING
        else -> InformationRequestNoticeDeliveryState.PENDING
    }

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
        private val COMBINED_ORDER = listOf(
            InformationRequestNoticeDeliveryState.PENDING,
            InformationRequestNoticeDeliveryState.CLAIMED,
            InformationRequestNoticeDeliveryState.RETRYING,
            InformationRequestNoticeDeliveryState.RENDERED,
            InformationRequestNoticeDeliveryState.DELIVERED,
            InformationRequestNoticeDeliveryState.FAILED,
            InformationRequestNoticeDeliveryState.UNDELIVERABLE,
        )
    }
}
