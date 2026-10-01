package com.docuhyphen.app.api.service.audit

import com.docuhyphen.app.api.model.recordpreservation.RecordOwnerRef
import com.docuhyphen.app.api.model.recordpreservation.RecordPreservationKey
import com.docuhyphen.app.api.service.audit.catalog.AuditCategory
import com.docuhyphen.app.api.service.recordpreservation.RecordPreservationHoldService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.time.Instant
import java.util.*

@ApplicationScoped
class AuditDisposalEligibilityService @Inject constructor(
    private val auditRetentionPolicyService: AuditRetentionPolicyService,
    private val holds: RecordPreservationHoldService,
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

        if (resourceType != null && resourceId != null &&
            holds.isPreserved(
                RecordOwnerRef.organization(organizationId),
                listOf(RecordPreservationKey(resourceType, resourceId, direct = true))
            )
        )
        {
            return false
        }

        return true
    }
}
