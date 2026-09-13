package com.docuhyphen.app.api.service.subscription

import com.docuhyphen.app.api.model.entity.SubscriptionFeatureEntitlement
import com.docuhyphen.app.api.repository.subscription.SubscriptionFeatureEntitlementRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/** One requested decision: the feature code it is about and whether the owner is to hold it. */
data class FeatureEntitlementDecision(
    val featureCode: String,
    val enabled: Boolean,
)

/**
 * The decisions an owner holds after a replacement, with the state on either side of it rendered
 * for the administration record. The before state is rendered before anything is written, because
 * an updated row is the same row and would otherwise already read as its new value.
 */
data class FeatureEntitlementReplacement(
    val beforeSnapshot: String,
    val afterSnapshot: String,
    val entitlements: List<SubscriptionFeatureEntitlement>,
)

/**
 * Records the platform-administered feature decisions of one subscription owner, whether that owner
 * is an organization or an individual account.
 *
 * Both administration surfaces write through here so a decision is stored, normalized, and rendered
 * the same way whoever it is about. The owner kind decides which column holds the owner and which
 * rows a replacement may touch, so two owners never overwrite each other's decisions.
 */
@ApplicationScoped
class SubscriptionFeatureEntitlementAdminService @Inject constructor(
    private val subscriptionFeatureEntitlementRepository: SubscriptionFeatureEntitlementRepository,
)
{
    companion object
    {
        /** How many features one owner may be decided about in a single request. */
        const val MAX_DECISIONS_PER_OWNER: Int = 100

        private val FEATURE_CODE_PATTERN = Regex("[A-Z][A-Z0-9_]{0,63}")
    }

    /** The decisions recorded against one owner, read from the column that owner kind uses. */
    fun findDecisions(owner: SubscriptionContext): List<SubscriptionFeatureEntitlement> =
        when (owner.ownerType)
        {
            SubscriptionOwnerType.ORGANIZATION ->
                subscriptionFeatureEntitlementRepository.findByOrganizationId(owner.ownerId)

            SubscriptionOwnerType.USER ->
                subscriptionFeatureEntitlementRepository.findByAppUserId(owner.ownerId)
        }

    /** The decisions recorded against several organizations, for a listing that shows them all. */
    fun findOrganizationDecisions(
        organizationIds: Collection<UUID>,
    ): List<SubscriptionFeatureEntitlement> =
        subscriptionFeatureEntitlementRepository.findByOrganizationIds(organizationIds)

    /**
     * Makes the requested decisions the complete set the owner holds. A code the request omits is
     * withdrawn, a code it repeats is updated in place, and a new code is recorded against the owner
     * that the context names. The request is validated in full before anything is written, so a
     * refused request leaves the stored decisions exactly as they were.
     */
    fun replaceDecisions(
        owner: SubscriptionContext,
        requested: List<FeatureEntitlementDecision>,
        actorId: UUID,
    ): FeatureEntitlementReplacement
    {
        val normalized = normalize(requested)
        val existing = findDecisions(owner)
        val existingByCode = existing.associateBy { it.featureCode }
        val beforeSnapshot = snapshot(existing)
        val now = Timestamp.from(Instant.now())

        existing.filter { it.featureCode !in normalized }
            .forEach(subscriptionFeatureEntitlementRepository::delete)

        normalized.forEach { (featureCode, enabled) ->
            val stored = existingByCode[featureCode]
            val decision = stored ?: newDecision(owner, featureCode, now)
            decision.isEnabled = enabled
            decision.updatedByAppUserId = actorId
            decision.updatedDate = now
            if (stored == null)
            {
                subscriptionFeatureEntitlementRepository.save(decision)
            }
            else
            {
                subscriptionFeatureEntitlementRepository.update(decision)
            }
        }

        val updated = findDecisions(owner)
        return FeatureEntitlementReplacement(
            beforeSnapshot = beforeSnapshot,
            afterSnapshot = snapshot(updated),
            entitlements = updated,
        )
    }

    private fun newDecision(
        owner: SubscriptionContext,
        featureCode: String,
        now: Timestamp,
    ): SubscriptionFeatureEntitlement = SubscriptionFeatureEntitlement().apply {
        this.ownerType = owner.ownerType.name
        when (owner.ownerType)
        {
            SubscriptionOwnerType.ORGANIZATION -> this.organizationId = owner.ownerId
            SubscriptionOwnerType.USER -> this.appUserId = owner.ownerId
        }
        this.featureCode = featureCode
        this.createdDate = now
    }

    private fun normalize(requested: List<FeatureEntitlementDecision>): Map<String, Boolean>
    {
        require(requested.size <= MAX_DECISIONS_PER_OWNER) {
            "At most $MAX_DECISIONS_PER_OWNER feature entitlements may be configured"
        }
        val codes = requested.map { it.featureCode.trim().uppercase() }
        require(codes.distinct().size == codes.size) { "Feature entitlement codes must be unique" }
        require(codes.all { it.matches(FEATURE_CODE_PATTERN) }) {
            "Feature entitlement codes must contain only uppercase letters, numbers, and underscores"
        }
        return codes.zip(requested.map { it.enabled }).toMap()
    }

    private fun snapshot(entitlements: List<SubscriptionFeatureEntitlement>): String =
        entitlements.sortedBy { it.featureCode }
            .joinToString(",") { "${it.featureCode}=${it.isEnabled}" }
}
