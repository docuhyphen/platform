package com.docuhyphen.app.api.service.subscription

import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.interceptor.EnforceAdminAction
import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.model.entity.Organization
import com.docuhyphen.app.api.model.entity.OrganizationSubscriptionPolicy
import com.docuhyphen.app.api.model.subscription.PolicyListResult
import com.docuhyphen.app.api.model.subscription.PolicyResult
import com.docuhyphen.app.api.model.subscription.SubscriptionLifecycleUpdate
import com.docuhyphen.app.api.resource.model.PlatformOrganizationSubscriptionPolicyRequest
import com.docuhyphen.app.api.model.auth.AdminApprovalContext
import com.docuhyphen.app.api.service.auth.AuthAuditService
import com.docuhyphen.app.api.service.auth.UserRoleService
import com.docuhyphen.app.api.service.organization.OrganizationService
import io.quarkus.security.ForbiddenException
import io.quarkus.security.UnauthorizedException
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.sql.Timestamp
import java.time.Instant
import java.util.*

@RequestScoped
class PlatformOrganizationSubscriptionPolicyService @Inject constructor(
    private val authTokenContext: AuthTokenContext,
    private val organizationService: OrganizationService,
    private val subscriptionPolicyService: SubscriptionPolicyService,
    private val authAuditService: AuthAuditService,
    private val userRoleService: UserRoleService,
    private val organizationMembershipService: com.docuhyphen.app.api.service.organization.OrganizationMembershipService,
    private val subscriptionLifecycleValidator: SubscriptionLifecycleValidator,
)
{
    companion object
    {
        val ORGANIZATION_TIER_CODE: String = PlanCatalog.DEFAULT_ORGANIZATION_PLAN.name

        val UNASSIGNED_SEAT_CAPACITY: Long? = null
    }

    fun getEffectivePolicy(organizationId: String, requestId: String?): PolicyResult
    {
        val actor = requirePlatformAdmin("PLATFORM_ORG_SUBSCRIPTION_POLICY_VIEW")
        val organization = requireOrganization(organizationId)
        val policy = subscriptionPolicyService.ensureOrganizationPolicy(organization.id)
        val result = persistedPolicyResult(policy, organization)

        authAuditService.emit(
            action = "PLATFORM_ORG_SUBSCRIPTION_POLICY_VIEW",
            outcome = "SUCCESS",
            actorId = actor.id,
            requestId = requestId,
            reason = "Platform admin viewed organization subscription policy",
            afterSnapshot = snapshot(result),
            targetType = "ORGANIZATION",
            targetId = organization.id.toString(),
        )

        return result
    }

    fun listPolicies(
        organizationId: String,
        limit: Int,
        offset: Int,
        tierCode: String?,
        persistedOnly: Boolean?,
        requestId: String?,
    ): PolicyListResult
    {
        val actor = requirePlatformAdmin("PLATFORM_ORG_SUBSCRIPTION_POLICY_LIST")
        validatePaging(limit, offset)
        val normalizedTierCode = tierCode?.trim()?.uppercase()?.takeIf { it.isNotBlank() }
        val allItems = if (organizationId.equals("all", ignoreCase = true))
        {
            buildGlobalList()
        }
        else
        {
            buildOrganizationScopedList(organizationId)
        }

        val filtered = allItems
            .asSequence()
            .filter { normalizedTierCode == null || it.tierCode == normalizedTierCode }
            .toList()

        val paged = filtered.drop(offset).take(limit)

        authAuditService.emit(
            action = "PLATFORM_ORG_SUBSCRIPTION_POLICY_LIST",
            outcome = "SUCCESS",
            actorId = actor.id,
            requestId = requestId,
            reason = "Platform admin listed organization subscription policies",
            afterSnapshot = "organizationId=$organizationId;tierCode=${normalizedTierCode ?: "*"};persistedOnly=${persistedOnly ?: false};total=${filtered.size};limit=$limit;offset=$offset;returned=${paged.size}",
        )

        return PolicyListResult(
            total = filtered.size,
            limit = limit,
            offset = offset,
            items = paged,
        )
    }

    @EnforceAdminAction("PLATFORM_ORG_SUBSCRIPTION_POLICY_UPSERT")
    @Transactional
    fun upsertPolicy(
        organizationId: String,
        request: PlatformOrganizationSubscriptionPolicyRequest,
        adminApprovalContext: AdminApprovalContext,
    ): PolicyResult
    {
        val actor = requirePlatformAdmin("PLATFORM_ORG_SUBSCRIPTION_POLICY_UPSERT")
        val organization = requireOrganization(organizationId)
        val normalizedTierCode = normalizeTierCode(request.tierCode)
        val lifecycle = lifecycleUpdate(request, normalizedTierCode)
        validateRequest(request, lifecycle)

        subscriptionPolicyService.ensureOrganizationPolicy(organization.id)
        val existing = subscriptionPolicyService.findOrganizationPolicyForUpdate(organization.id)
            ?: throw IllegalStateException("Organization subscription policy could not be locked")

        val beforeSnapshot = snapshot(existing, organization)

        val now = Timestamp.from(Instant.now())
        val policy = existing

        policy.tierCode = normalizedTierCode
        policy.maxUsers = request.maxUsers
        policy.subscriptionStatus = lifecycle.status.name
        policy.billingFrequency = lifecycle.billingFrequency?.name
        policy.currentPeriodStart = lifecycle.currentPeriodStart?.let(Timestamp::from)
        policy.currentPeriodEnd = lifecycle.currentPeriodEnd?.let(Timestamp::from)
        policy.gracePeriodEnd = lifecycle.gracePeriodEnd?.let(Timestamp::from)
        policy.changeReason = lifecycle.changeReason
        policy.updatedDate = now

        subscriptionPolicyService.updateOrganizationPolicy(policy)

        val result = PolicyResult(
            organizationId = organization.id,
            tierCode = policy.tierCode,
            maxUsers = policy.maxUsers,
            currentActiveUsers = activeUserCount(organization),
            subscriptionStatus = policy.subscriptionStatus,
            billingFrequency = policy.billingFrequency,
            currentPeriodStart = policy.currentPeriodStart,
            currentPeriodEnd = policy.currentPeriodEnd,
            gracePeriodEnd = policy.gracePeriodEnd,
            changeReason = policy.changeReason,
            persisted = true,
            createdDate = policy.createdDate,
            updatedDate = policy.updatedDate,
        )

        authAuditService.emitRequired(
            action = "PLATFORM_ORG_SUBSCRIPTION_POLICY_UPSERT",
            outcome = "SUCCESS",
            actorId = actor.id,
            actorRole = "APP_ADMIN",
            requestId = adminApprovalContext.requestId,
            reason = lifecycle.changeReason,
            beforeSnapshot = beforeSnapshot,
            afterSnapshot = snapshot(result),
            targetType = "ORGANIZATION",
            targetId = organization.id.toString(),
            structuredDetails = mapOf(
                "before_state" to beforeSnapshot,
                "after_state" to snapshot(result),
            ),
        )

        return result
    }

    private fun requirePlatformAdmin(attemptedAction: String): AppUser
    {
        val currentUser = authTokenContext.authToken.appUser
            ?: throw UnauthorizedException("User is not authenticated")

        if (!userRoleService.isAppAdmin(currentUser.id))
        {
            authAuditService.emit(
                action = attemptedAction,
                outcome = "DENIED",
                actorId = currentUser.id,
                reason = "Caller lacks effective App Administrator privilege",
                targetType = "PLATFORM_ORGANIZATION",
            )
            throw ForbiddenException("User does not have permission to manage organization subscription policies")
        }

        return currentUser
    }

    private fun requireOrganization(organizationId: String): Organization
    {
        val orgId = requireUuid(organizationId, "organization ID")
        return runCatching { organizationService.getOrganizationById(orgId) }
            .getOrElse { throw IllegalArgumentException("Organization not found") }
    }

    private fun requireUuid(value: String, label: String): UUID
    {
        return runCatching { UUID.fromString(value) }
            .getOrElse { throw IllegalArgumentException("Invalid $label format") }
    }

    private fun validateRequest(
        request: PlatformOrganizationSubscriptionPolicyRequest,
        lifecycle: SubscriptionLifecycleUpdate,
    )
    {
        subscriptionLifecycleValidator.validate(SubscriptionOwnerType.ORGANIZATION, lifecycle)

        if (request.maxUsers != null && request.maxUsers <= 0)
        {
            throw IllegalArgumentException("Max users must be greater than 0 when provided")
        }

    }

    private fun lifecycleUpdate(
        request: PlatformOrganizationSubscriptionPolicyRequest,
        normalizedTierCode: String,
    ): SubscriptionLifecycleUpdate
    {
        val planCode = PlanCode.fromCodeOrNull(normalizedTierCode)
            ?: throw IllegalArgumentException("Unknown subscription tier code: $normalizedTierCode")
        return SubscriptionLifecycleUpdate(
            planCode = planCode,
            status = SubscriptionStatus.fromCode(request.subscriptionStatus),
            billingFrequency = parseBillingFrequency(request.billingFrequency),
            currentPeriodStart = parseInstant(request.currentPeriodStart, "current period start"),
            currentPeriodEnd = parseInstant(request.currentPeriodEnd, "current period end"),
            gracePeriodEnd = parseInstant(request.gracePeriodEnd, "grace period end"),
            changeReason = request.changeReason.trim(),
        )
    }

    private fun parseBillingFrequency(value: String?): BillingFrequency?
    {
        if (value.isNullOrBlank()) return null
        return BillingFrequency.fromCodeOrNull(value)
            ?: throw IllegalArgumentException("Unknown billing frequency: $value")
    }

    private fun parseInstant(value: String?, label: String): Instant?
    {
        if (value.isNullOrBlank()) return null
        return runCatching { Instant.parse(value.trim()) }
            .getOrElse { throw IllegalArgumentException("Invalid $label; use an ISO-8601 UTC timestamp") }
    }

    private fun normalizeTierCode(tierCode: String): String
    {
        return tierCode.trim().uppercase()
    }

    private fun activeUserCount(organization: Organization): Long
    {
        return organizationMembershipService.membersOf(organization.id)
            .count { it.isActive && it.deprovisionedAt == null }.toLong()
    }

    private fun validatePaging(limit: Int, offset: Int)
    {
        if (limit !in 1..200)
        {
            throw IllegalArgumentException("Limit must be between 1 and 200")
        }

        if (offset < 0)
        {
            throw IllegalArgumentException("Offset must be greater than or equal to 0")
        }
    }

    private fun buildGlobalList(): List<PolicyResult>
    {
        return organizationService.findAllOrganizations()
            .map { organization ->
                persistedPolicyResult(
                    subscriptionPolicyService.ensureOrganizationPolicy(organization.id),
                    organization,
                )
            }
            .sortedByDescending { it.updatedDate?.time ?: 0L }
    }

    private fun buildOrganizationScopedList(organizationId: String): List<PolicyResult>
    {
        val organization = requireOrganization(organizationId)
        val policy = subscriptionPolicyService.ensureOrganizationPolicy(organization.id)
        return listOf(persistedPolicyResult(policy, organization))
    }

    private fun persistedPolicyResult(policy: OrganizationSubscriptionPolicy, organization: Organization): PolicyResult
    {
        return PolicyResult(
            organizationId = organization.id,
            tierCode = policy.tierCode,
            maxUsers = resolveEffectiveMaxUsers(policy.maxUsers),
            currentActiveUsers = activeUserCount(organization),
            subscriptionStatus = policy.subscriptionStatus,
            billingFrequency = policy.billingFrequency,
            currentPeriodStart = policy.currentPeriodStart,
            currentPeriodEnd = policy.currentPeriodEnd,
            gracePeriodEnd = policy.gracePeriodEnd,
            changeReason = policy.changeReason,
            persisted = true,
            createdDate = policy.createdDate,
            updatedDate = policy.updatedDate,
        )
    }

    private fun resolveEffectiveMaxUsers(configuredMaxUsers: Long?): Long?
    {
        return configuredMaxUsers
    }

    private fun snapshot(policy: OrganizationSubscriptionPolicy, organization: Organization): String
    {
        return "organizationId=${organization.id};tierCode=${policy.tierCode};maxUsers=${policy.maxUsers};subscriptionStatus=${policy.subscriptionStatus};billingFrequency=${policy.billingFrequency};currentPeriodStart=${policy.currentPeriodStart};currentPeriodEnd=${policy.currentPeriodEnd};gracePeriodEnd=${policy.gracePeriodEnd};changeReason=${policy.changeReason};createdDate=${policy.createdDate};updatedDate=${policy.updatedDate}"
    }

    private fun snapshot(result: PolicyResult): String
    {
        return "organizationId=${result.organizationId};tierCode=${result.tierCode};maxUsers=${result.maxUsers};currentActiveUsers=${result.currentActiveUsers};subscriptionStatus=${result.subscriptionStatus};billingFrequency=${result.billingFrequency};currentPeriodStart=${result.currentPeriodStart};currentPeriodEnd=${result.currentPeriodEnd};gracePeriodEnd=${result.gracePeriodEnd};persisted=${result.persisted};changeReason=${result.changeReason};createdDate=${result.createdDate};updatedDate=${result.updatedDate}"
    }
}
