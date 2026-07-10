package com.docuhyphen.app.api.service.audit

import com.docuhyphen.app.api.service.audit.catalog.AuditCategory
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.time.Instant
import java.util.UUID

/**
 * Combines [AuditRetentionPolicyService] and [AuditLegalHoldService] into the single disposal
 * check a future retention-purge job must call before removing anything from the
 * searchable-projection store: legal hold always overrides retention-driven disposal, and a
 * category whose effective policy is not [com.docuhyphen.app.api.model.entity.AuditIdentityTreatment]
 * eligible is never disposed by this service. No job in this codebase performs disposal yet - this
 * is the eligibility check the job will call, kept separate so it is independently unit-testable.
 */
@ApplicationScoped
class AuditDisposalEligibilityService @Inject constructor(
    private val auditRetentionPolicyService: AuditRetentionPolicyService,
    private val auditLegalHoldService: AuditLegalHoldService,
)
{
    fun isEligibleForDisposal(
        organizationId: UUID,
        category: AuditCategory,
        occurredAt: Instant,
        resourceType: String? = null,
        resourceId: String? = null,
        at: Instant = Instant.now(),
    ): Boolean
    {
        if (!auditRetentionPolicyService.isLedgerRetentionExpired(organizationId, category, occurredAt, at))
        {
            return false
        }

        if (resourceType != null && resourceId != null && auditLegalHoldService.isUnderHold(organizationId, resourceType, resourceId))
        {
            return false
        }

        return true
    }
}
