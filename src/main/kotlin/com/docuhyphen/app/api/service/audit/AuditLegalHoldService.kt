package com.docuhyphen.app.api.service.audit

import com.docuhyphen.app.api.exception.RecordPreservationException
import com.docuhyphen.app.api.exception.RecordPreservationNotFoundException
import com.docuhyphen.app.api.exception.RecordPreservationRequestException
import com.docuhyphen.app.api.model.entity.RecordPreservationHold
import com.docuhyphen.app.api.model.entity.RecordPreservationHoldStatus
import com.docuhyphen.app.api.model.entity.RecordPreservationScope
import com.docuhyphen.app.api.model.recordpreservation.PlaceRecordPreservationHoldCommand
import com.docuhyphen.app.api.model.recordpreservation.RecordOwnerRef
import com.docuhyphen.app.api.model.recordpreservation.RecordPreservationKey
import com.docuhyphen.app.api.model.recordpreservation.ReleaseRecordPreservationHoldCommand
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.recordpreservation.RecordPreservationHoldService
import com.docuhyphen.app.api.service.subscription.OrganizationFeatureSubscriptionGuard
import com.docuhyphen.app.api.service.subscription.PlanFeature
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.util.UUID

@ApplicationScoped
class AuditLegalHoldService @Inject constructor(
    private val holds: RecordPreservationHoldService,
    private val auditDeniedAttemptService: AuditDeniedAttemptService,
    private val subscriptionGuard: OrganizationFeatureSubscriptionGuard,
)
{
    @Transactional
    fun placeHold(
        organizationId: UUID?,
        resourceType: String,
        resourceId: String,
        reason: String,
        caseReference: String?,
        placedByUserId: UUID,
    ): RecordPreservationHold
    {
        subscriptionGuard.requireMutation(organizationId, PlanFeature.AUDIT_GOVERNANCE)
        return guarded {
            holds.place(
                PlaceRecordPreservationHoldCommand(
                    owner = ownerOf(organizationId),
                    resourceType = resourceType,
                    resourceId = resourceId,
                    scope = RecordPreservationScope.RESOURCE,
                    reason = reason,
                    caseReference = caseReference,
                    effectiveFrom = null,
                    principal = PrincipalRef.user(placedByUserId),
                ),
            ).hold
        }
    }

    @Transactional
    fun releaseHold(holdId: UUID, expectedOrganizationId: UUID?, releasedByUserId: UUID): RecordPreservationHold
    {
        subscriptionGuard.requireMutation(expectedOrganizationId, PlanFeature.AUDIT_GOVERNANCE)
        return guarded {
            holds.release(
                ReleaseRecordPreservationHoldCommand(
                    holdId = holdId,
                    owner = ownerOf(expectedOrganizationId),
                    reason = RELEASE_REASON,
                    principal = PrincipalRef.user(releasedByUserId),
                ),
            ).hold
        }
    }

    fun isUnderHold(organizationId: UUID?, resourceType: String, resourceId: String): Boolean =
        holds.isPreserved(ownerOf(organizationId), listOf(RecordPreservationKey(resourceType, resourceId, direct = true)))

    fun listActiveHolds(organizationId: UUID?): List<RecordPreservationHold> =
        holds.holds(ownerOf(organizationId), setOf(RecordPreservationHoldStatus.ACTIVE))

    fun recordDeniedAttempt(actorId: UUID, organizationId: UUID, reasonCode: String)
    {
        auditDeniedAttemptService.record(
            actorId,
            organizationId,
            "AUDIT_LEGAL_HOLD",
            organizationId.toString(),
            reasonCode,
        )
    }

    private fun ownerOf(organizationId: UUID?): RecordOwnerRef =
        organizationId?.let(RecordOwnerRef::organization) ?: RecordOwnerRef.PLATFORM

    private fun <T> guarded(block: () -> T): T =
        try
        {
            block()
        }
        catch (exception: RecordPreservationNotFoundException)
        {
            throw AuditLegalHoldNotFoundException()
        }
        catch (exception: RecordPreservationRequestException)
        {
            throw IllegalArgumentException(exception.message)
        }
        catch (exception: RecordPreservationException)
        {
            throw IllegalArgumentException(exception.message)
        }

    private companion object
    {
        const val RELEASE_REASON = "Released through audit governance"
    }
}

class AuditLegalHoldNotFoundException : IllegalArgumentException("Legal hold not found")
