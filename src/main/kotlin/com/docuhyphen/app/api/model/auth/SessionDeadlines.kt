package com.docuhyphen.app.api.model.auth

import java.time.Instant

data class SessionDeadlines(
    val idleExpiresAt: Instant,
    val sessionExpiresAt: Instant?,
)
