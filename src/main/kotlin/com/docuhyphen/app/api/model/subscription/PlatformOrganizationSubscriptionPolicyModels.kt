package com.docuhyphen.app.api.model.subscription

import java.sql.Timestamp
import java.util.UUID

data class PolicyResult(
    val organizationId: UUID,
    val tierCode: String,
    val maxUsers: Long?,
    val currentActiveUsers: Long,
    val subscriptionStatus: String,
    val billingFrequency: String?,
    val currentPeriodStart: Timestamp?,
    val currentPeriodEnd: Timestamp?,
    val gracePeriodEnd: Timestamp?,
    val changeReason: String?,
    val persisted: Boolean,
    val createdDate: Timestamp?,
    val updatedDate: Timestamp?,
)

data class PolicyListResult(
    val total: Int,
    val limit: Int,
    val offset: Int,
    val items: List<PolicyResult>,
)
