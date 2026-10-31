package com.docuhyphen.app.api.service.informationrequest.disposal

import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.disposal.InformationRequestDisposalOutcome
import com.docuhyphen.app.api.model.informationrequest.disposal.InformationRequestDisposalRun
import com.docuhyphen.app.api.model.recordpreservation.RecordPreservationResourceTypes
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.service.recordpreservation.RecordDisposalService
import com.docuhyphen.app.api.service.recordpreservation.RecordRetentionScheduleService
import com.docuhyphen.app.api.service.subscription.SubscriptionAccessService
import com.docuhyphen.app.api.service.subscription.SubscriptionContext
import com.docuhyphen.app.api.service.subscription.SubscriptionStatus
import io.quarkus.narayana.jta.QuarkusTransaction
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.slf4j.LoggerFactory
import java.sql.Timestamp
import java.time.Clock
import java.time.Duration
import java.util.*

@ApplicationScoped
class InformationRequestDisposalWorker @Inject constructor(
    private val disposals: RecordDisposalService,
    private val schedules: RecordRetentionScheduleService,
    private val requestRepository: InformationRequestRepository,
    private val disposalService: InformationRequestDisposalService,
    private val clock: Clock,
    private val subscriptions: SubscriptionAccessService,
)
{
    fun run(limit: Int = BATCH_SIZE): InformationRequestDisposalRun
    {
        var finalized = 0
        var pending = 0
        var refused = 0
        QuarkusTransaction.requiringNew().call { disposals.openClaimIds(limit) }.forEach { claimId ->
            if (process(claimId) == RecordDisposalState.FINALIZED) finalized++ else pending++
        }
        QuarkusTransaction.requiringNew()
            .call { schedules.currentSchedules(RecordPreservationResourceTypes.INFORMATION_REQUEST) }
            .filter { it.disposalAfterDays != null }
            .forEach { schedule ->
                val outcome = runCatching {
                    if (ownerOperationallySuspended(schedule)) InformationRequestDisposalRun(0, 0, 0)
                    else disposeScheduled(schedule, limit)
                }.getOrElse { failure ->
                    logger.warn(
                        "Retention schedule {} could not be processed; other owners continue",
                        schedule.id,
                        failure
                    )
                    InformationRequestDisposalRun(0, 1, 0)
                }
                finalized += outcome.finalized
                pending += outcome.pending
                refused += outcome.refused
            }
        return InformationRequestDisposalRun(finalized, pending, refused)
    }

    private fun disposeScheduled(schedule: RecordRetentionSchedule, limit: Int): InformationRequestDisposalRun {
        val disposalDays = requireNotNull(schedule.disposalAfterDays)
        require(disposalDays in 0..365_000 && schedule.minimumRetentionDays in 0..disposalDays) {
            "A retention schedule has bounded, ordered ages"
        }
        var finalized = 0
        var pending = 0
        var refused = 0
        val cutoff = Timestamp.from(
            clock.instant().minus(Duration.ofDays(disposalDays.toLong()))
        )
        var after: UUID? = null
        var claimed = 0
        do {
            val page = QuarkusTransaction.requiringNew().call {
                requestRepository.findDisposalCandidateIds(
                    ownerTypeOf(schedule),
                    schedule.ownerId,
                    cutoff,
                    after,
                    limit
                )
            }
            for (requestId in page) {
                if (claimed >= limit) break
                when (val outcome = claim(requestId)) {
                    is InformationRequestDisposalOutcome.Claimed -> {
                        claimed++
                        if (process(outcome.view.claim.id) == RecordDisposalState.FINALIZED) finalized++ else pending++
                    }

                    is InformationRequestDisposalOutcome.Refused -> refused++
                    null -> pending++
                }
            }
            after = page.lastOrNull()
        } while (page.size == limit && claimed < limit)
        return InformationRequestDisposalRun(finalized, pending, refused)
    }

    private fun claim(requestId: UUID): InformationRequestDisposalOutcome? =
        runCatching {
            disposalService.claim(
                requestId,
                RecordDisposalBasis.RETENTION_SCHEDULE,
                null,
                InformationRequestDisposalService.SYSTEM
            )
        }.getOrElse { failure ->
            logger.warn(
                "Information Request {} could not be claimed for disposal; it will be retried",
                requestId,
                failure
            )
            null
        }

    private fun process(claimId: UUID): RecordDisposalState =
        runCatching { disposalService.process(claimId) }
            .getOrElse { failure ->
                logger.warn("Disposal claim {} did not complete; it will be retried", claimId, failure)
                RecordDisposalState.CLAIMED
            }

    private fun ownerOperationallySuspended(schedule: RecordRetentionSchedule): Boolean {
        val owner = when (schedule.ownerKind) {
            RecordOwnerKind.ORGANIZATION -> SubscriptionContext.forOrganization(schedule.ownerId)
            RecordOwnerKind.USER -> SubscriptionContext.forUser(schedule.ownerId)
            RecordOwnerKind.PLATFORM -> return false
        }
        return QuarkusTransaction.requiringNew()
            .call { subscriptions.resolve(owner).status == SubscriptionStatus.SUSPENDED }
    }

    private fun ownerTypeOf(schedule: RecordRetentionSchedule): InformationRequestOwnerType =
        if (schedule.ownerKind == RecordOwnerKind.ORGANIZATION) InformationRequestOwnerType.ORGANIZATION else InformationRequestOwnerType.USER

    private companion object
    {
        const val BATCH_SIZE = 50
        val logger = LoggerFactory.getLogger(InformationRequestDisposalWorker::class.java)
    }
}
