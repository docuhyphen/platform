package com.docuhyphen.app.api.service.informationrequest.disposal

import com.docuhyphen.app.api.model.entity.InformationRequestOwnerType
import com.docuhyphen.app.api.model.entity.RecordDisposalBasis
import com.docuhyphen.app.api.model.entity.RecordDisposalState
import com.docuhyphen.app.api.model.entity.RecordOwnerKind
import com.docuhyphen.app.api.model.informationrequest.disposal.InformationRequestDisposalOutcome
import com.docuhyphen.app.api.model.informationrequest.disposal.InformationRequestDisposalRun
import com.docuhyphen.app.api.model.recordpreservation.RecordPreservationResourceTypes
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.service.recordpreservation.RecordDisposalService
import com.docuhyphen.app.api.service.recordpreservation.RecordRetentionScheduleService
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
                val cutoff = Timestamp.from(
                    clock.instant().minus(Duration.ofDays(requireNotNull(schedule.disposalAfterDays).toLong()))
                )
                val ownerType =
                    if (schedule.ownerKind == RecordOwnerKind.ORGANIZATION) InformationRequestOwnerType.ORGANIZATION else InformationRequestOwnerType.USER
                QuarkusTransaction.requiringNew()
                    .call { requestRepository.findFinishedIdsBefore(ownerType, schedule.ownerId, cutoff, limit) }
                    .forEach { requestId ->
                        when (val outcome = runCatching {
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
                        })
                        {
                            is InformationRequestDisposalOutcome.Claimed ->
                                if (process(outcome.view.claim.id) == RecordDisposalState.FINALIZED) finalized++ else pending++

                            is InformationRequestDisposalOutcome.Refused -> refused++
                            null -> pending++
                        }
                    }
            }
        return InformationRequestDisposalRun(finalized, pending, refused)
    }

    private fun process(claimId: UUID): RecordDisposalState =
        runCatching { disposalService.process(claimId) }
            .getOrElse { failure ->
                logger.warn("Disposal claim {} did not complete; it will be retried", claimId, failure)
                RecordDisposalState.CLAIMED
            }

    private companion object
    {
        const val BATCH_SIZE = 50
        val logger = LoggerFactory.getLogger(InformationRequestDisposalWorker::class.java)
    }
}
