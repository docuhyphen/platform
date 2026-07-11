package com.docuhyphen.app.api.service.audit

import com.docuhyphen.app.api.model.entity.AuditIdentityTreatment
import com.docuhyphen.app.api.service.audit.catalog.AuditCategory
import jakarta.enterprise.context.ApplicationScoped

/** Effective retention parameters for one [AuditCategory], either the platform default or an organization override. */
data class RetentionPolicySpec(
    val category: AuditCategory,
    val ledgerRetentionDays: Int,
    val archiveRetentionDays: Int,
    val legalHoldEligible: Boolean,
    val identityTreatment: AuditIdentityTreatment,
    val isOverride: Boolean,
)

/**
 * Platform-default retention catalog by [AuditCategory].
 *
 * The "Retention / legal-hold / residency / replication / destruction rules" and "Online search
 * window vs archive-only history" prerequisite decisions are still unanswered by compliance/legal
 * are not yet finalized, so the numbers below are conservative,
 * documented placeholders, not confirmed policy: [DEFAULT_LEDGER_RETENTION_DAYS] mirrors a
 * roughly 13-month searchable-projection window, and [DEFAULT_ARCHIVE_RETENTION_DAYS] matches the
 * existing `AuditArchiveRetentionDays` CloudFormation parameter default (2555 days / ~7 years)
 * already governing the S3 Object Lock bucket, so the two never silently disagree. Every
 * category defaults to legal-hold eligible and fully readable identity fields until an
 * organization or a future compliance decision narrows it.
 */
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
