package com.docuhyphen.app.api.service.informationrequest.lifecycle

import com.docuhyphen.app.api.model.entity.InformationRequestLineageKind
import com.docuhyphen.app.api.model.entity.InformationRequestRefreshRule
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestFollowUpSpec
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestRefreshSource
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

object InformationRequestRefreshSchedule {
    private val FIRST_RETRY_DELAY: Duration = Duration.ofHours(1)
    private val LONGEST_RETRY_DELAY: Duration = Duration.ofHours(24)
    private const val MAXIMUM_DOUBLINGS = 5

    fun isDue(rule: InformationRequestRefreshRule, source: InformationRequestRefreshSource, now: Instant): Boolean =
        ChronoUnit.DAYS.between(LocalDate.ofInstant(now, ZoneOffset.UTC), source.expiresOn) <= rule.leadDays

    fun dueOn(rule: InformationRequestRefreshRule, source: InformationRequestRefreshSource): LocalDate =
        source.expiresOn.minusDays(rule.leadDays.toLong())

    fun followUpOf(rule: InformationRequestRefreshRule, source: InformationRequestRefreshSource) =
        InformationRequestFollowUpSpec(
            kind = InformationRequestLineageKind.REFRESH,
            sourcePackageId = source.packageId,
            refreshRuleId = rule.id,
        )

    fun nextAttemptAt(now: Instant, attemptCount: Int): Instant {
        val delay = FIRST_RETRY_DELAY.multipliedBy(1L shl (attemptCount - 1).coerceIn(0, MAXIMUM_DOUBLINGS))
        return now.plus(minOf(delay, LONGEST_RETRY_DELAY))
    }
}
