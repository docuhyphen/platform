package com.docuhyphen.app.api.service.auth

import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.interceptor.EnforceAdminAction
import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.model.entity.Organization
import com.docuhyphen.app.api.model.entity.OrganizationSubscriptionPolicy
import com.docuhyphen.app.api.repository.OrganizationRepository
import com.docuhyphen.app.api.repository.OrganizationSubscriptionPolicyRepository
import com.docuhyphen.app.api.resource.model.PlatformOrganizationSubscriptionPolicyRequest
import io.quarkus.security.UnauthorizedException
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

data class PolicyResult(
    val organizationId: UUID,
    val tierCode: String,
    val maxUsers: Long?,
    val currentActiveUsers: Long,
    val changeReason: String?,
    val persisted: Boolean,
    val createdDate: Timestamp?,
    val updatedDate: Timestamp?,
)

data class PolicyListResult(
    val total: Int,
    val limit: Int,
    val offset: Int,
    val items: List<PolicyResult>,
)

@RequestScoped
class PlatformOrganizationSubscriptionPolicyService @Inject constructor(
    private val authTokenContext: AuthTokenContext,
    private val organizationRepository: OrganizationRepository,
    private val organizationSubscriptionPolicyRepository: OrganizationSubscriptionPolicyRepository,
    private val authAuditService: AuthAuditService,
    private val userRoleService: UserRoleService,
    private val organizationMembershipService: com.docuhyphen.app.api.service.organization.OrganizationMembershipService,
)
{
    companion object
    {
        const val FREE_TIER_CODE = "FREE"
        const val FREE_TIER_MAX_USERS: Long = 3
    }


    fun getEffectivePolicy(organizationId: String, requestId: String?): PolicyResult
    {
        val actor = requirePlatformAdmin()
        val organization = requireOrganization(organizationId)
        val existing = organizationSubscriptionPolicyRepository.findByOrganizationId(organization.id)

        val result = if (existing == null)
        {
            PolicyResult(
                organizationId = organization.id,
                tierCode = FREE_TIER_CODE,
                maxUsers = FREE_TIER_MAX_USERS,
                currentActiveUsers = activeUserCount(organization),
                changeReason = "Implicit default free-tier policy",
                persisted = false,
                createdDate = null,
                updatedDate = null,
            )
        }
        else
        {
            PolicyResult(
                organizationId = organization.id,
                tierCode = existing.tierCode,
                maxUsers = existing.maxUsers,
                currentActiveUsers = activeUserCount(organization),
                changeReason = existing.changeReason,
                persisted = true,
                createdDate = existing.createdDate,
                updatedDate = existing.updatedDate,
            )
        }

        authAuditService.emit(
            action = "PLATFORM_ORG_SUBSCRIPTION_POLICY_VIEW",
            outcome = "SUCCESS",
            actorId = actor.id,
            organizationId = organization.id,
            requestId = requestId,
            reason = "Platform admin viewed organization subscription policy",
            afterSnapshot = snapshot(result),
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
        val actor = requirePlatformAdmin()
        validatePaging(limit, offset)
        val normalizedTierCode = tierCode?.trim()?.uppercase()?.takeIf { it.isNotBlank() }
        val includeDefaults = persistedOnly != true

        val allItems = if (organizationId.equals("all", ignoreCase = true))
        {
            buildGlobalList(includeDefaults)
        }
        else
        {
            buildOrganizationScopedList(organizationId, includeDefaults)
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
        val actor = requirePlatformAdmin()
        val organization = requireOrganization(organizationId)
        val normalizedTierCode = normalizeTierCode(request.tierCode)
        validateRequest(request, normalizedTierCode)

        val existing = organizationSubscriptionPolicyRepository.findByOrganizationId(organization.id)

        val beforeSnapshot = existing?.let { snapshot(it, organization) }

        val now = Timestamp.from(Instant.now())
        val policy = existing ?: OrganizationSubscriptionPolicy().apply {
            this.organization = organization
            this.createdDate = now
        }

        policy.tierCode = normalizedTierCode
        policy.maxUsers = request.maxUsers
        policy.changeReason = request.changeReason?.trim()?.takeIf { it.isNotBlank() }
        policy.updatedDate = now

        if (existing == null)
        {
            organizationSubscriptionPolicyRepository.save(policy)
        }
        else
        {
            organizationSubscriptionPolicyRepository.update(policy)
        }

        val result = PolicyResult(
            organizationId = organization.id,
            tierCode = policy.tierCode,
            maxUsers = policy.maxUsers,
            currentActiveUsers = activeUserCount(organization),
            changeReason = policy.changeReason,
            persisted = true,
            createdDate = policy.createdDate,
            updatedDate = policy.updatedDate,
        )

        authAuditService.emit(
            action = "PLATFORM_ORG_SUBSCRIPTION_POLICY_UPSERT",
            outcome = "SUCCESS",
            actorId = actor.id,
            organizationId = organization.id,
            requestId = adminApprovalContext.requestId,
            reason = "Platform admin updated organization subscription policy",
            beforeSnapshot = beforeSnapshot,
            afterSnapshot = snapshot(result),
        )

        return result
    }

    @EnforceAdminAction("PLATFORM_ORG_SUBSCRIPTION_POLICY_DELETE")
    @Transactional
    fun deletePolicy(organizationId: String, adminApprovalContext: AdminApprovalContext): PolicyResult
    {
        val actor = requirePlatformAdmin()
        val organization = requireOrganization(organizationId)
        val existing = organizationSubscriptionPolicyRepository.findByOrganizationId(organization.id)
            ?: throw IllegalArgumentException("Organization subscription policy not found")

        val beforeSnapshot = snapshot(existing, organization)
        organizationSubscriptionPolicyRepository.delete(existing)

        val result = PolicyResult(
            organizationId = organization.id,
            tierCode = FREE_TIER_CODE,
            maxUsers = FREE_TIER_MAX_USERS,
            currentActiveUsers = activeUserCount(organization),
            changeReason = "Policy reset to platform default",
            persisted = false,
            createdDate = null,
            updatedDate = null,
        )

        authAuditService.emit(
            action = "PLATFORM_ORG_SUBSCRIPTION_POLICY_DELETE",
            outcome = "SUCCESS",
            actorId = actor.id,
            organizationId = organization.id,
            requestId = adminApprovalContext.requestId,
            reason = "Platform admin deleted organization subscription policy",
            beforeSnapshot = beforeSnapshot,
            afterSnapshot = snapshot(result),
        )

        return result
    }

    private fun requirePlatformAdmin(): AppUser
    {
        val currentUser = authTokenContext.authToken.appUser
            ?: throw UnauthorizedException("User is not authenticated")

        if (!userRoleService.isAppAdmin(currentUser.id))
        {
            throw UnauthorizedException("User does not have permission to manage organization subscription policies")
        }

        return currentUser
    }

    private fun requireOrganization(organizationId: String): Organization
    {
        val orgId = requireUuid(organizationId, "organization ID")
        return organizationRepository.findById(orgId)
            ?: throw IllegalArgumentException("Organization not found")
    }

    private fun requireUuid(value: String, label: String): UUID
    {
        return runCatching { UUID.fromString(value) }
            .getOrElse { throw IllegalArgumentException("Invalid $label format") }
    }

    private fun validateRequest(request: PlatformOrganizationSubscriptionPolicyRequest, normalizedTierCode: String)
    {
        if (normalizedTierCode.isBlank())
        {
            throw IllegalArgumentException("Tier code is required")
        }

        if (request.maxUsers != null && request.maxUsers <= 0)
        {
            throw IllegalArgumentException("Max users must be greater than 0 when provided")
        }

        if (request.changeReason != null && request.changeReason.length > 1024)
        {
            throw IllegalArgumentException("Change reason must be at most 1024 characters")
        }
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

    private fun buildGlobalList(includeDefaults: Boolean): List<PolicyResult>
    {
        val persisted = organizationSubscriptionPolicyRepository.findAll()
            .sortedByDescending { it.updatedDate.time }
            .map { policy ->
                val organization = policy.organization ?: throw IllegalStateException("Organization reference is missing for subscription policy")
                persistedPolicyResult(policy, organization)
            }

        if (!includeDefaults)
        {
            return persisted
        }

        val persistedOrgIds = persisted.map { it.organizationId }.toSet()
        val defaults = organizationRepository.findAll()
            .filter { it.id !in persistedOrgIds }
            .map { defaultPolicyResult(it) }

        return persisted + defaults
    }

    private fun buildOrganizationScopedList(organizationId: String, includeDefaults: Boolean): List<PolicyResult>
    {
        val organization = requireOrganization(organizationId)
        val persisted = organizationSubscriptionPolicyRepository.findByOrganizationId(organization.id)
        if (persisted != null)
        {
            return listOf(persistedPolicyResult(persisted, organization))
        }

        return if (includeDefaults)
        {
            listOf(defaultPolicyResult(organization))
        }
        else
        {
            emptyList()
        }
    }

    private fun persistedPolicyResult(policy: OrganizationSubscriptionPolicy, organization: Organization): PolicyResult
    {
        return PolicyResult(
            organizationId = organization.id,
            tierCode = policy.tierCode,
            maxUsers = resolveEffectiveMaxUsers(policy.tierCode, policy.maxUsers),
            currentActiveUsers = activeUserCount(organization),
            changeReason = policy.changeReason,
            persisted = true,
            createdDate = policy.createdDate,
            updatedDate = policy.updatedDate,
        )
    }

    private fun defaultPolicyResult(organization: Organization): PolicyResult
    {
        return PolicyResult(
            organizationId = organization.id,
            tierCode = FREE_TIER_CODE,
            maxUsers = FREE_TIER_MAX_USERS,
            currentActiveUsers = activeUserCount(organization),
            changeReason = "Implicit default free-tier policy",
            persisted = false,
            createdDate = null,
            updatedDate = null,
        )
    }

    private fun resolveEffectiveMaxUsers(tierCode: String, configuredMaxUsers: Long?): Long?
    {
        if (configuredMaxUsers != null)
        {
            return configuredMaxUsers
        }

        return if (tierCode.equals(FREE_TIER_CODE, ignoreCase = true)) FREE_TIER_MAX_USERS else null
    }

    private fun snapshot(policy: OrganizationSubscriptionPolicy, organization: Organization): String
    {
        return "organizationId=${organization.id};tierCode=${policy.tierCode};maxUsers=${policy.maxUsers};changeReason=${policy.changeReason};createdDate=${policy.createdDate};updatedDate=${policy.updatedDate}"
    }

    private fun snapshot(result: PolicyResult): String
    {
        return "organizationId=${result.organizationId};tierCode=${result.tierCode};maxUsers=${result.maxUsers};currentActiveUsers=${result.currentActiveUsers};persisted=${result.persisted};changeReason=${result.changeReason};createdDate=${result.createdDate};updatedDate=${result.updatedDate}"
    }
}



