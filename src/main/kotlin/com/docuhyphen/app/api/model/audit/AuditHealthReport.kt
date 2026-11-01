package com.docuhyphen.app.api.model.audit

data class AuditHealthReport(
    val outboxBacklogSize: Int,
    val oldestOutboxAgeMinutes: Long?,
    val ledgerEventCount: Long,
    val analyticsFactCount: Long,
    val analyticsProjectionLag: Long,
)
