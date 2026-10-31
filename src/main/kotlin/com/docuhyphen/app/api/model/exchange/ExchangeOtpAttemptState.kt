package com.docuhyphen.app.api.model.exchange

import java.time.Instant

internal data class ExchangeOtpAttemptState(
    var failedAttempts: Int = 0,
    var lockedUntil: Instant? = null,
)
