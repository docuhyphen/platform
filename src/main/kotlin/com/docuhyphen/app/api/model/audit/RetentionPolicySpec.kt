package com.docuhyphen.app.api.model.audit

import com.docuhyphen.app.api.model.entity.AuditIdentityTreatment
import com.docuhyphen.app.api.service.audit.catalog.AuditCategory

data class RetentionPolicySpec(
    val category: AuditCategory,
    val ledgerRetentionDays: Int,
    val archiveRetentionDays: Int,
    val legalHoldEligible: Boolean,
    val identityTreatment: AuditIdentityTreatment,
    val isOverride: Boolean,
)
