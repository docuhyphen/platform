package com.docuhyphen.app.api.model.notification

data class DispatchResult(val delivered: Int, val retried: Int, val failed: Int)

data class OutboxBacklogHealth(
    val pending: Long,
    val oldestPendingAgeSeconds: Long,
    val repeatedFailures: Long,
    val exhausted: Long,
)
