package com.docuhyphen.app.api.service.subscription

import com.docuhyphen.app.api.model.entity.Organization
import com.docuhyphen.app.api.model.entity.OrganizationSubscriptionPolicy
import com.docuhyphen.app.api.model.entity.UserSubscriptionPolicy
import com.docuhyphen.app.api.model.subscription.SubscriptionContext
import com.docuhyphen.app.api.repository.subscription.OrganizationSubscriptionPolicyRepository
import com.docuhyphen.app.api.repository.subscription.SubscriptionFeatureEntitlementRepository
import com.docuhyphen.app.api.repository.subscription.UserSubscriptionPolicyRepository
import com.docuhyphen.app.api.service.organization.OrganizationService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import java.sql.Timestamp
import java.time.Instant
import java.util.*

@ApplicationScoped
class SubscriptionPolicyService @Inject constructor(
    private val userSubscriptionPolicyRepository: UserSubscriptionPolicyRepository,
    private val organizationSubscriptionPolicyRepository: OrganizationSubscriptionPolicyRepository,
    private val subscriptionFeatureEntitlementRepository: SubscriptionFeatureEntitlementRepository,
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

    fun featureOverrides(context: SubscriptionContext): Map<PlanFeature, Boolean>
    {
        val entitlements = when (context.ownerType)
        {
            SubscriptionOwnerType.ORGANIZATION ->
                subscriptionFeatureEntitlementRepository.findByOrganizationId(context.ownerId)

            SubscriptionOwnerType.USER ->
                subscriptionFeatureEntitlementRepository.findByAppUserId(context.ownerId)
        }

        return entitlements
            .mapNotNull { entitlement ->
                PlanFeature.fromCodeOrNull(entitlement.featureCode)?.let { it to entitlement.isEnabled }
            }
            .toMap()
    }
}

