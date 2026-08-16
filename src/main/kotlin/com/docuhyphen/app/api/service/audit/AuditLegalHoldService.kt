package com.docuhyphen.app.api.service.audit

import com.docuhyphen.app.api.model.entity.AuditLegalHold
import com.docuhyphen.app.api.model.entity.AuditLegalHoldStatus
import com.docuhyphen.app.api.repository.audit.AuditLegalHoldRepository
import com.docuhyphen.app.api.service.audit.catalog.AuditActorKind
import com.docuhyphen.app.api.service.audit.catalog.AuditEventType
import com.docuhyphen.app.api.service.audit.catalog.AuditOutcome
import com.docuhyphen.app.api.service.subscription.OrganizationFeatureSubscriptionGuard
import com.docuhyphen.app.api.service.subscription.PlanFeature
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/**
 * Places and releases holds on a denormalized resource reference. A hold never
 * touches the WORM ledger/archive - it only records the fact that disposal of anything
 * referencing that resource must not proceed while [isUnderHold] is true. Legal hold overrides
 * disposal: callers of any future retention-purge job must consult this before disposing.
 */
@ApplicationScoped
class AuditLegalHoldService @Inject constructor(
    private val auditLegalHoldRepository: AuditLegalHoldRepository,
    private val auditRecorder: AuditRecorder,
    private val auditDeniedAttemptService: AuditDeniedAttemptService,
    private val subscriptionGuard: OrganizationFeatureSubscriptionGuard,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(AuditLegalHoldService::class.java)
    }

    @Transactional
    fun placeHold(
        organizationId: UUID?,
        resourceType: String,
        resourceId: String,
        reason: String,
        caseReference: String?,
        placedByUserId: UUID,
    ): AuditLegalHold
    {
        subscriptionGuard.requireMutation(organizationId, PlanFeature.AUDIT_GOVERNANCE)
        require(resourceType.isNotBlank()) { "resourceType is required" }
        require(resourceId.isNotBlank()) { "resourceId is required" }
        require(reason.isNotBlank()) { "reason is required" }

        val now = Timestamp.from(Instant.now())
        val hold = AuditLegalHold().apply {
            this.organizationId = organizationId
            this.resourceType = resourceType.trim()
            this.resourceId = resourceId.trim()
            this.reason = reason.trim()
            this.caseReference = caseReference?.trim()?.takeIf { it.isNotBlank() }
            this.status = AuditLegalHoldStatus.ACTIVE
            this.placedByUserId = placedByUserId
            this.placedAt = now
            this.createdAt = now
            this.updatedAt = now
        }
        val saved = auditLegalHoldRepository.save(hold)
        recordEvent(AuditEventType.AUDIT_LEGAL_HOLD_PLACED, placedByUserId, saved)
        return saved
    }

    @Transactional
    fun releaseHold(holdId: UUID, expectedOrganizationId: UUID?, releasedByUserId: UUID): AuditLegalHold
    {
        val hold = auditLegalHoldRepository.findById(holdId) ?: throw AuditLegalHoldNotFoundException()
        if (hold.organizationId != expectedOrganizationId)
        {
            throw AuditLegalHoldNotFoundException()
        }
        subscriptionGuard.requireMutation(hold.organizationId, PlanFeature.AUDIT_GOVERNANCE)
        require(hold.status == AuditLegalHoldStatus.ACTIVE) { "Only an ACTIVE legal hold can be released" }

        val now = Timestamp.from(Instant.now())
        hold.status = AuditLegalHoldStatus.RELEASED
        hold.releasedByUserId = releasedByUserId
        hold.releasedAt = now
        hold.updatedAt = now
        val saved = auditLegalHoldRepository.update(hold)
        recordEvent(AuditEventType.AUDIT_LEGAL_HOLD_RELEASED, releasedByUserId, saved)
        return saved
    }

    fun isUnderHold(organizationId: UUID?, resourceType: String, resourceId: String): Boolean =
        auditLegalHoldRepository.findActiveForResource(organizationId, resourceType, resourceId).isNotEmpty()

    fun listActiveHolds(organizationId: UUID?): List<AuditLegalHold> =
        auditLegalHoldRepository.findActiveForOrganization(organizationId)

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

    private fun recordEvent(eventType: AuditEventType, actorId: UUID, hold: AuditLegalHold)
    {
        try
        {
            auditRecorder.record(
                AuditEventDraft(
                    eventTypeKey = eventType.key,
                    outcome = AuditOutcome.SUCCESS,
                    actorId = actorId,
                    actorKind = AuditActorKind.HUMAN,
                    actorRole = "AUDIT_GOVERNANCE",
                    owner = hold.organizationId?.let(AuditOwnerScope::Organization) ?: AuditOwnerScope.Platform,
                    targetType = "AUDIT_LEGAL_HOLD",
                    targetId = hold.id.toString(),
                    targetLabel = hold.caseReference?.let { "${hold.reason} ($it)" } ?: hold.reason,
                    payload = buildMap {
                        put("resource_type", hold.resourceType)
                        put("resource_id", hold.resourceId)
                        put("status", hold.status.name)
                        hold.caseReference?.let { put("case_reference", it) }
                    },
                )
            )
        }
        catch (e: AuditDraftInvalidException)
        {
            logger.warn("AuditLegalHoldService: AuditRecorder rejected {}: {}", eventType, e.message)
        }
        catch (e: AuditCaptureFailedException)
        {
            logger.error("AuditLegalHoldService: AuditRecorder capture failed for {}: {}", eventType, e.message, e)
        }
    }
}

class AuditLegalHoldNotFoundException : IllegalArgumentException("Legal hold not found")
