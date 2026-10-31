package com.docuhyphen.app.api.service.informationrequest.lifecycle

import com.docuhyphen.app.api.repository.informationrequest.lifecycle.InformationRequestRefreshRuleRepository
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.scheduler.Scheduled
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.context.control.ActivateRequestContext
import jakarta.inject.Inject
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.*

@ApplicationScoped
class InformationRequestRefreshScheduler @Inject constructor(
    private val refreshRules: InformationRequestRefreshRuleRepository,
    private val scheduledRefreshes: InformationRequestScheduledRefreshService,
    private val clock: Clock,
) {
    @Scheduled(
        every = "\${app.information-request.refresh.every:1h}",
        identity = "information-request-refresh",
        concurrentExecution = Scheduled.ConcurrentExecution.SKIP,
    )
    @ActivateRequestContext
    fun tick() {
        try {
            val created = processDueRefreshes(clock.instant())
            if (created > 0) logger.info("information request refreshes created={}", created)
        } catch (exception: Exception) {
            logger.error("information request refresh pass failed", exception)
        }
    }

    fun processDueRefreshes(now: Instant): Int {
        val ruleIds = QuarkusTransaction.requiringNew().call {
            refreshRules.findDueRuleIds(
                LocalDate.ofInstant(now, ZoneOffset.UTC),
                now,
                BATCH_SIZE,
                InformationRequestTransitionMatrix.successorSourceStates(),
                InformationRequestTransitionMatrix.mutableParentStatuses(),
            )
        }
        var created = 0
        ruleIds.forEach { ruleId ->
            try {
                if (QuarkusTransaction.requiringNew().call { scheduledRefreshes.refreshDue(ruleId, now) }) created++
            } catch (exception: Exception) {
                defer(ruleId, now, exception)
            }
        }
        return created
    }

    private fun defer(ruleId: UUID, now: Instant, failure: Exception) {
        try {
            val retryAt = QuarkusTransaction.requiringNew().call { scheduledRefreshes.deferRefresh(ruleId, now) }
            logger.warn(
                "information request refresh rule {} could not create its refresh; it will be retried at {}",
                ruleId,
                retryAt,
                failure
            )
        } catch (deferral: Exception) {
            logger.error("information request refresh rule {} could not record when to retry", ruleId, deferral)
        }
    }

    private companion object {
        const val BATCH_SIZE = 50
        val logger = LoggerFactory.getLogger(InformationRequestRefreshScheduler::class.java)
    }
}
