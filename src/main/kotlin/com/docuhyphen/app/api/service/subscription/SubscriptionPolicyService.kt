package com.docuhyphen.app.api.service.subscription

import com.docuhyphen.app.api.model.entity.Organization
import com.docuhyphen.app.api.model.entity.OrganizationSubscriptionPolicy
import com.docuhyphen.app.api.model.entity.UserSubscriptionPolicy
import com.docuhyphen.app.api.repository.OrganizationFeatureEntitlementRepository
import com.docuhyphen.app.api.repository.OrganizationSubscriptionPolicyRepository
import com.docuhyphen.app.api.repository.UserSubscriptionPolicyRepository
import com.docuhyphen.app.api.service.organization.OrganizationService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/**
 * Owns persistence of subscription records.
 *
 * Every registered individual account holds exactly one individual plan row and every
 * organization holds exactly one Business row. Keeping that guarantee here means callers never
 * have to reason about a missing record or fall back to an implicit default.
 */
@ApplicationScoped
class SubscriptionPolicyService @Inject constructor(
    private val userSubscriptionPolicyRepository: UserSubscriptionPolicyRepository,
    private val organizationSubscriptionPolicyRepository: OrganizationSubscriptionPolicyRepository,
    private val organizationFeatureEntitlementRepository: OrganizationFeatureEntitlementRepository,
    private val organizationService: OrganizationService,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(SubscriptionPolicyService::class.java)
    }

    fun findUserPolicy(appUserId: UUID): UserSubscriptionPolicy?
    {
        return userSubscriptionPolicyRepository.findByAppUserId(appUserId)
    }

    fun findUserPolicyForUpdate(appUserId: UUID): UserSubscriptionPolicy?
    {
        return userSubscriptionPolicyRepository.findByAppUserIdForUpdate(appUserId)
    }

    fun findUserPolicies(appUserIds: Collection<UUID>): List<UserSubscriptionPolicy>
    {
        return userSubscriptionPolicyRepository.findByAppUserIds(appUserIds)
    }

    fun updateUserPolicy(policy: UserSubscriptionPolicy)
    {
        userSubscriptionPolicyRepository.update(policy)
    }

    fun updateOrganizationPolicy(policy: OrganizationSubscriptionPolicy)
    {
        organizationSubscriptionPolicyRepository.update(policy)
    }

    fun findOrganizationPolicy(organizationId: UUID): OrganizationSubscriptionPolicy?
    {
        return organizationSubscriptionPolicyRepository.findByOrganizationId(organizationId)
    }

    fun findOrganizationPolicyForUpdate(organizationId: UUID): OrganizationSubscriptionPolicy?
    {
        return organizationSubscriptionPolicyRepository.findByOrganizationIdForUpdate(organizationId)
    }

    fun findOrganizationPolicies(organizationIds: Collection<UUID>): List<OrganizationSubscriptionPolicy>
    {
        return organizationSubscriptionPolicyRepository.findByOrganizationIds(organizationIds)
    }

    fun findAllOrganizationPolicies(): List<OrganizationSubscriptionPolicy>
    {
        return organizationSubscriptionPolicyRepository.findAll()
    }

    fun saveOrganizationPolicy(policy: OrganizationSubscriptionPolicy)
    {
        organizationSubscriptionPolicyRepository.save(policy)
    }

    fun deleteOrganizationPolicy(policy: OrganizationSubscriptionPolicy)
    {
        organizationSubscriptionPolicyRepository.delete(policy)
    }

    /**
     * Guarantees a registered account owns an individual subscription. New accounts start on the
     * default individual plan. An account that already has a record keeps it untouched so a
     * later sign-in never resets a paid plan back to the default.
     */
    @Transactional
    fun ensureUserPolicy(
        appUserId: UUID,
        planCode: PlanCode = PlanCatalog.DEFAULT_USER_PLAN,
        changeReason: String = "Subscription created for a newly registered account",
    ): UserSubscriptionPolicy
    {
        PlanCatalog.requireAssignableTo(planCode, SubscriptionOwnerType.USER)

        val existing = userSubscriptionPolicyRepository.findByAppUserId(appUserId)
        if (existing != null)
        {
            return existing
        }

        val now = Timestamp.from(Instant.now())
        val policy = UserSubscriptionPolicy().apply {
            this.appUserId = appUserId
            this.planCode = planCode.name
            this.subscriptionStatus = SubscriptionStatus.ACTIVE.name
            this.changeReason = changeReason
            this.createdDate = now
            this.updatedDate = now
        }

        userSubscriptionPolicyRepository.save(policy)
        logger.info("Created {} subscription for app user {}", planCode, appUserId)

        return policy
    }

    /**
     * Guarantees an organization owns a Business subscription. Seat capacity is deliberately
     * left unassigned so a platform administrator records purchased seats explicitly rather than
     * an implicit cap locking members out.
     */
    @Transactional
    fun ensureOrganizationPolicy(
        organization: Organization,
        changeReason: String = "Business subscription created when the organization became active",
    ): OrganizationSubscriptionPolicy
    {
        val existing = organizationSubscriptionPolicyRepository.findByOrganizationId(organization.id)
        if (existing != null)
        {
            return existing
        }

        val now = Timestamp.from(Instant.now())
        val policy = OrganizationSubscriptionPolicy().apply {
            this.organization = organization
            this.tierCode = PlanCatalog.DEFAULT_ORGANIZATION_PLAN.name
            this.maxUsers = null
            this.subscriptionStatus = SubscriptionStatus.ACTIVE.name
            this.changeReason = changeReason
            this.createdDate = now
            this.updatedDate = now
        }

        organizationSubscriptionPolicyRepository.save(policy)
        logger.info("Created Business subscription for organization {}", organization.id)

        return policy
    }

    /**
     * Guarantees a Business subscription for an organization identified only by ID, used when a
     * plan decision needs the record and the caller does not already hold the organization.
     */
    @Transactional
    fun ensureOrganizationPolicy(organizationId: UUID): OrganizationSubscriptionPolicy
    {
        val existing = organizationSubscriptionPolicyRepository.findByOrganizationId(organizationId)
        if (existing != null)
        {
            return existing
        }

        val organization = organizationService.getOrganizationById(organizationId)

        return ensureOrganizationPolicy(organization)
    }

    /**
     * Platform-administered decisions layered on top of an organization's plan defaults. An
     * entry set to true adds a feature the plan omits and false removes one it grants. Codes
     * that do not correspond to a known product feature are ignored so an obsolete override row
     * can never change what a plan resolves to.
     */
    fun organizationFeatureOverrides(organizationId: UUID): Map<PlanFeature, Boolean>
    {
        return organizationFeatureEntitlementRepository.findByOrganizationId(organizationId)
            .mapNotNull { entitlement ->
                PlanFeature.fromCodeOrNull(entitlement.featureCode)?.let { it to entitlement.isEnabled }
            }
            .toMap()
    }
}



