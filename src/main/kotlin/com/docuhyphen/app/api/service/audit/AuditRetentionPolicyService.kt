package com.docuhyphen.app.api.service.audit

import com.docuhyphen.app.api.model.entity.AuditIdentityTreatment
import com.docuhyphen.app.api.model.entity.AuditRetentionPolicy
import com.docuhyphen.app.api.repository.audit.AuditRetentionPolicyRepository
import com.docuhyphen.app.api.service.audit.catalog.AuditActorKind
import com.docuhyphen.app.api.service.audit.catalog.AuditCategory
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
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Organization-override layer over [AuditRetentionCatalogService]'s platform-default catalog
 * An organization row narrows or widens the default for exactly one
 * [AuditCategory]; with no row, [getEffectivePolicy] returns the platform default unchanged.
 */
@ApplicationScoped
class AuditRetentionPolicyService @Inject constructor(
    private val auditRetentionPolicyRepository: AuditRetentionPolicyRepository,
    private val auditRetentionCatalogService: AuditRetentionCatalogService,
    private val auditRecorder: AuditRecorder,
    private val subscriptionGuard: OrganizationFeatureSubscriptionGuard,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(AuditRetentionPolicyService::class.java)
    }

    fun getEffectivePolicy(organizationId: UUID, category: AuditCategory): RetentionPolicySpec
    {
        val override = auditRetentionPolicyRepository.findByOrganizationAndCategory(organizationId, category.name)
            ?: return auditRetentionCatalogService.defaultsFor(category)

        return RetentionPolicySpec(
            category = category,
            ledgerRetentionDays = override.ledgerRetentionDays,
            archiveRetentionDays = override.archiveRetentionDays,
            legalHoldEligible = override.legalHoldEligible,
            identityTreatment = override.identityTreatment,
            isOverride = true,
        )
    }

    fun listEffectivePolicies(organizationId: UUID): List<RetentionPolicySpec> =
        auditRetentionCatalogService.allCategories().map { getEffectivePolicy(organizationId, it) }

    @Transactional
    fun upsertOverride(
        organizationId: UUID,
        category: AuditCategory,
        ledgerRetentionDays: Int,
        archiveRetentionDays: Int,
        legalHoldEligible: Boolean,
        identityTreatment: AuditIdentityTreatment,
        updatedByUserId: UUID,
    ): RetentionPolicySpec
    {
        subscriptionGuard.requireMutation(organizationId, PlanFeature.AUDIT_GOVERNANCE)
        require(ledgerRetentionDays > 0) { "ledgerRetentionDays must be positive" }
        require(archiveRetentionDays > 0) { "archiveRetentionDays must be positive" }

        val existing = auditRetentionPolicyRepository.findByOrganizationAndCategory(organizationId, category.name)
        val now = Timestamp.from(Instant.now())
        val policy = existing ?: AuditRetentionPolicy().apply {
            this.organizationId = organizationId
            this.category = category.name
            this.createdAt = now
        }
        policy.ledgerRetentionDays = ledgerRetentionDays
        policy.archiveRetentionDays = archiveRetentionDays
        policy.legalHoldEligible = legalHoldEligible
        policy.identityTreatment = identityTreatment
        policy.updatedByUserId = updatedByUserId
        policy.updatedAt = now

        val saved = if (existing == null) auditRetentionPolicyRepository.save(policy) else auditRetentionPolicyRepository.update(policy)

        recordEvent(
            eventType = AuditEventType.AUDIT_RETENTION_POLICY_UPDATED,
            organizationId = organizationId,
            actorId = updatedByUserId,
            targetId = category.name,
            payload = mapOf(
                "category" to category.name,
                "ledger_retention_days" to ledgerRetentionDays.toString(),
                "archive_retention_days" to archiveRetentionDays.toString(),
                "legal_hold_eligible" to legalHoldEligible.toString(),
                "identity_treatment" to identityTreatment.name,
            ),
        )

        return getEffectivePolicy(organizationId, category)
    }

    /** Whether an event that occurred at [occurredAt] has passed the searchable-projection retention window. */
    fun isLedgerRetentionExpired(organizationId: UUID, category: AuditCategory, occurredAt: Instant, at: Instant = Instant.now()): Boolean
    {
        val policy = getEffectivePolicy(organizationId, category)
        return ChronoUnit.DAYS.between(occurredAt, at) >= policy.ledgerRetentionDays
    }

    private fun recordEvent(eventType: AuditEventType, organizationId: UUID, actorId: UUID, targetId: String, payload: Map<String, String>)
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
                    owner = AuditOwnerScope.Organization(organizationId),
                    targetType = "AUDIT_RETENTION_POLICY",
                    targetId = targetId,
                    payload = payload,
                )
            )
        }
        catch (e: AuditDraftInvalidException)
        {
            logger.warn("AuditRetentionPolicyService: AuditRecorder rejected {}: {}", eventType, e.message)
        }
        catch (e: AuditCaptureFailedException)
        {
            logger.error("AuditRetentionPolicyService: AuditRecorder capture failed for {}: {}", eventType, e.message, e)
        }
    }
}
