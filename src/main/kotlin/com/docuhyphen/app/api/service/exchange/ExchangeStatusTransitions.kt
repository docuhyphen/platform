package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.exception.ExchangeStatusTransitionException
import com.docuhyphen.app.api.model.entity.ExchangeStatus

object ExchangeStatusTransitions {
    private val OPEN_TARGETS = setOf(
        ExchangeStatus.ACCEPTED_STARTED,
        ExchangeStatus.REJECTED,
        ExchangeStatus.ENDED,
        ExchangeStatus.RESCINDED,
    )

    private val ALLOWED: Map<ExchangeStatus, Set<ExchangeStatus>> = mapOf(
        ExchangeStatus.INITIATED to OPEN_TARGETS,
        ExchangeStatus.ACCEPTED_STARTED to OPEN_TARGETS,
        ExchangeStatus.ENDED to emptySet(),
        ExchangeStatus.REJECTED to emptySet(),
        ExchangeStatus.RESCINDED to emptySet(),
    )

    fun permits(from: ExchangeStatus, to: ExchangeStatus): Boolean = to in ALLOWED.getValue(from)

    fun requirePermitted(from: ExchangeStatus, to: ExchangeStatus) {
        if (!permits(from, to)) throw ExchangeStatusTransitionException(from, to)
    }
}
