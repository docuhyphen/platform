package com.docuhyphen.app.api.model.informationrequest.oversight

import java.time.Instant

enum class InformationRequestHealthIndicatorKey
{
    REQUESTS_WITHOUT_EXECUTION_GRANT,
    RESERVATIONS_ABOVE_CAP,
    NOTICE_INTENTS_OVERDUE,
    CONNECTOR_EXCHANGES_FAILED,
    DISPOSAL_CLAIMS_STALLED,
    EVENT_DELIVERY_BACKLOG,
}

data class InformationRequestHealthIndicator(
    val key: InformationRequestHealthIndicatorKey,
    val count: Long,
    val threshold: Long,
)
{
    val breached: Boolean get() = count > threshold
}

data class InformationRequestHealthReport(
    val checkedAt: Instant,
    val indicators: List<InformationRequestHealthIndicator>,
)
{
    val healthy: Boolean get() = indicators.none { it.breached }
}

data class InformationRequestHealthWindows(
    val noticeIntentMinutes: Long,
    val disposalClaimHours: Long,
    val eventBacklogMinutes: Long,
    val connectorFailureHours: Long,
)
