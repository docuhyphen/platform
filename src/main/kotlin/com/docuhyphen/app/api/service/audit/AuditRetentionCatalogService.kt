package com.docuhyphen.app.api.service.audit

import com.docuhyphen.app.api.model.audit.RetentionPolicySpec
import com.docuhyphen.app.api.model.entity.AuditIdentityTreatment
import com.docuhyphen.app.api.service.audit.catalog.AuditCategory
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class AuditRetentionCatalogService
{
    companion object
    {
        const val DEFAULT_LEDGER_RETENTION_DAYS: Int = 400
        const val DEFAULT_ARCHIVE_RETENTION_DAYS: Int = 2555
    }

    fun defaultsFor(category: AuditCategory): RetentionPolicySpec = RetentionPolicySpec(
        category = category,
        ledgerRetentionDays = DEFAULT_LEDGER_RETENTION_DAYS,
        archiveRetentionDays = DEFAULT_ARCHIVE_RETENTION_DAYS,
        legalHoldEligible = true,
        identityTreatment = AuditIdentityTreatment.READABLE,
        isOverride = false,
    )

    fun allCategories(): List<AuditCategory> = AuditCategory.entries
}
