package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequestNoticeAttemptOutcome
import com.docuhyphen.app.api.model.informationrequest.InformationRequestNoticeDispatchResult
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestNoticeIntentRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestOutboundNoticeRepository
import io.quarkus.narayana.jta.QuarkusTransaction
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.slf4j.LoggerFactory
import java.time.Clock
import java.util.UUID

@ApplicationScoped
class InformationRequestNoticeWorker @Inject constructor(
    private val intentRepository: InformationRequestNoticeIntentRepository,
    private val noticeRepository: InformationRequestOutboundNoticeRepository,
    private val dispatcher: InformationRequestNoticeDispatcher,
    private val clock: Clock,
)
{
    fun dispatchPending(limit: Int = BATCH_SIZE): InformationRequestNoticeDispatchResult =
        dispatch(
            QuarkusTransaction.requiringNew().call { intentRepository.findUnclaimedIds(limit) },
        ) {
            QuarkusTransaction.requiringNew().call {
                noticeRepository.findAwaitingDeliveryIds(InformationRequestNoticeStateReader.MAXIMUM_ATTEMPTS, limit)
            }
        }

    fun dispatchForRequest(requestId: UUID): InformationRequestNoticeDispatchResult =
        dispatch(
            QuarkusTransaction.requiringNew().call { intentRepository.findUnclaimedIdsForRequest(requestId) },
        ) {
            QuarkusTransaction.requiringNew().call {
                noticeRepository.findAwaitingDeliveryIds(InformationRequestNoticeStateReader.MAXIMUM_ATTEMPTS, BATCH_SIZE, requestId)
            }
        }

    private fun dispatch(intentIds: List<UUID>, noticeIds: () -> List<UUID>): InformationRequestNoticeDispatchResult
    {
        var rendered = 0
        intentIds.forEach { intentId ->
            runCatching { dispatcher.claimAndRender(intentId, clock.instant()) }
                .onSuccess { if (it != null) rendered++ }
                .onFailure { logger.warn("Information Request notice intent {} could not be rendered; it will be retried", intentId, it) }
        }
        var delivered = 0
        var failed = 0
        noticeIds().forEach { noticeId ->
            runCatching { dispatcher.deliver(noticeId, clock.instant()) }
                .onSuccess { outcome ->
                    when (outcome)
                    {
                        InformationRequestNoticeAttemptOutcome.DELIVERED -> delivered++
                        InformationRequestNoticeAttemptOutcome.FAILED -> failed++
                        else -> Unit
                    }
                }
                .onFailure { logger.warn("Information Request notice {} delivery could not be recorded; it will be retried", noticeId, it) }
        }
        return InformationRequestNoticeDispatchResult(rendered, delivered, failed)
    }

    private companion object
    {
        const val BATCH_SIZE = 100
        val logger = LoggerFactory.getLogger(InformationRequestNoticeWorker::class.java)
    }
}
