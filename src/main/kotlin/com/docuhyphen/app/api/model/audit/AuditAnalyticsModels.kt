package com.docuhyphen.app.api.model.audit

import java.util.UUID

data class AnalyticsProjectionResult(val projected: Int, val alreadyProjected: Int, val failed: Int)

data class AnalyticsReconciliationReport(
    val organizationId: UUID?,
    val platformOnly: Boolean,
    val ledgerEventCount: Long,
    val analyticsFactCount: Long,
    val matches: Boolean,
    val missingLedgerEventIds: List<UUID>,
)
