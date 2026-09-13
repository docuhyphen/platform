package com.docuhyphen.app.api.service.platform

import com.docuhyphen.app.api.exception.OrganizationNotFoundException
import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.interceptor.EnforceAdminAction
import com.docuhyphen.app.api.model.PlatformOrganizationDtoMapper
import com.docuhyphen.app.api.model.dto.PlatformOrganizationFeatureEntitlementsDto
import com.docuhyphen.app.api.model.dto.PlatformOrganizationFeatureEntitlementsUpdateRequest
import com.docuhyphen.app.api.model.dto.PlatformOrganizationListDto
import com.docuhyphen.app.api.model.dto.PlatformOrganizationSummaryDto
import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.model.entity.Organization
import com.docuhyphen.app.api.repository.organization.OrganizationRepository
import com.docuhyphen.app.api.repository.subscription.OrganizationSubscriptionPolicyRepository
import com.docuhyphen.app.api.service.auth.AdminApprovalContext
import com.docuhyphen.app.api.service.auth.AuthAuditService
import com.docuhyphen.app.api.service.auth.UserRoleService
import com.docuhyphen.app.api.service.organization.OrganizationMembershipService
import com.docuhyphen.app.api.service.subscription.FeatureEntitlementDecision
import com.docuhyphen.app.api.service.subscription.SubscriptionContext
import com.docuhyphen.app.api.service.subscription.SubscriptionFeatureEntitlementAdminService
import io.quarkus.security.UnauthorizedException
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.util.*

@RequestScoped
class PlatformOrganizationService @Inject constructor(
    private val authTokenContext: AuthTokenContext,
    private val userRoleService: UserRoleService,
    private val organizationRepository: OrganizationRepository,
    private val organizationSubscriptionPolicyRepository: OrganizationSubscriptionPolicyRepository,
    private val featureEntitlementAdminService: SubscriptionFeatureEntitlementAdminService,
    private val organizationMembershipService: OrganizationMembershipService,
    private val mapper: PlatformOrganizationDtoMapper,
    private val authAuditService: AuthAuditService,
)
{
    companion object
    {
        private const val MAX_CHANGE_REASON_LENGTH = 1024
    }

    fun list(
        query: String?,
        status: String?,
        tierCode: String?,
        sort: String?,
        direction: String?,
        limit: Int,
        offset: Int,
        requestId: String?,
    ): PlatformOrganizationListDto
    {
        val actor = requirePlatformAdmin("PLATFORM_ORGANIZATION_LIST")
        validatePaging(limit, offset)
        val normalizedQuery = query?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
        if (normalizedQuery != null && normalizedQuery.length > 100)
        {
            throw IllegalArgumentException("Search query must be at most 100 characters")
        }
        val active = parseStatus(status)
        val normalizedTierCode = tierCode?.trim()?.uppercase()?.takeIf { it.isNotBlank() }
        val sortField = parseSort(sort)
        val sortDirection = parseDirection(direction)

        val organizations = organizationRepository.findForPlatformAdministration(
            normalizedQuery = normalizedQuery,
            active = active,
            tierCode = normalizedTierCode,
            sort = sortField,
            direction = sortDirection,
            limit = limit,
            offset = offset,
        )
        val total = organizationRepository.countForPlatformAdministration(
            normalizedQuery = normalizedQuery,
            active = active,
            tierCode = normalizedTierCode,
        )
        val items = summaries(organizations)

        authAuditService.emit(
            action = "PLATFORM_ORGANIZATION_LIST",
            outcome = "SUCCESS",
            actorId = actor.id,
            requestId = requestId,
            reason = "Platform administrator listed restricted organization account summaries",
        )

        return PlatformOrganizationListDto(
            total = total,
            limit = limit,
            offset = offset,
            items = items,
        )
    }

    fun get(organizationId: String, requestId: String?): PlatformOrganizationSummaryDto
    {
        val actor = requirePlatformAdmin("PLATFORM_ORGANIZATION_VIEW")
        val organization = requireOrganization(organizationId)
        val result = summaries(listOf(organization)).single()
        authAuditService.emit(
            action = "PLATFORM_ORGANIZATION_VIEW",
            outcome = "SUCCESS",
            actorId = actor.id,
            requestId = requestId,
            reason = "Platform administrator viewed a restricted organization account summary",
            targetType = "ORGANIZATION",
            targetId = organization.id.toString(),
        )
        return result
    }

    fun getFeatureEntitlements(
        organizationId: String,
        requestId: String?,
    ): PlatformOrganizationFeatureEntitlementsDto
    {
        val actor = requirePlatformAdmin("PLATFORM_ORG_FEATURE_ENTITLEMENTS_VIEW")
        val organization = requireOrganization(organizationId)
        val entitlements = featureEntitlementAdminService.findDecisions(
            SubscriptionContext.forOrganization(organization.id),
        )
        authAuditService.emit(
            action = "PLATFORM_ORG_FEATURE_ENTITLEMENTS_VIEW",
            outcome = "SUCCESS",
            actorId = actor.id,
            requestId = requestId,
            reason = "Platform administrator viewed organization feature entitlements",
            targetType = "ORGANIZATION",
            targetId = organization.id.toString(),
        )
        return mapper.toEntitlements(organization.id, entitlements)
    }

    @EnforceAdminAction("PLATFORM_ORG_FEATURE_ENTITLEMENTS_UPDATE")
    @Transactional
    fun replaceFeatureEntitlements(
        organizationId: String,
        request: PlatformOrganizationFeatureEntitlementsUpdateRequest,
        adminApprovalContext: AdminApprovalContext,
    ): PlatformOrganizationFeatureEntitlementsDto
    {
        val actor = requirePlatformAdmin("PLATFORM_ORG_FEATURE_ENTITLEMENTS_UPDATE")
        val organization = requireOrganization(organizationId)
        validateChangeReason(request)

        val replacement = featureEntitlementAdminService.replaceDecisions(
            owner = SubscriptionContext.forOrganization(organization.id),
            requested = request.entitlements.map {
                FeatureEntitlementDecision(it.featureCode, it.enabled)
            },
            actorId = actor.id,
        )

        authAuditService.emitRequired(
            action = "PLATFORM_ORG_FEATURE_ENTITLEMENTS_UPDATE",
            outcome = "SUCCESS",
            actorId = actor.id,
            actorRole = "APP_ADMIN",
            requestId = adminApprovalContext.requestId,
            reason = request.changeReason?.trim()?.takeIf { it.isNotBlank() }
                ?: "Platform administrator replaced organization feature entitlements",
            beforeSnapshot = replacement.beforeSnapshot,
            afterSnapshot = replacement.afterSnapshot,
            targetType = "ORGANIZATION",
            targetId = organization.id.toString(),
            structuredDetails = mapOf(
                "before_state" to replacement.beforeSnapshot,
                "after_state" to replacement.afterSnapshot,
            ),
        )
        return mapper.toEntitlements(organization.id, replacement.entitlements)
    }

    private fun summaries(organizations: List<Organization>): List<PlatformOrganizationSummaryDto>
    {
        val organizationIds = organizations.map { it.id }
        val policies = organizationSubscriptionPolicyRepository.findByOrganizationIds(organizationIds)
            .associateBy { it.organization?.id }
        val entitlements = featureEntitlementAdminService.findOrganizationDecisions(organizationIds)
            .groupBy { it.organizationId }
        val activeUserCounts = organizationMembershipService.activeProvisionedMemberCounts(organizationIds)

        return organizations.map { organization ->
            mapper.toSummary(
                organization = organization,
                policy = policies[organization.id],
                activeUsers = activeUserCounts[organization.id] ?: 0,
                entitlements = entitlements[organization.id].orEmpty(),
            )
        }
    }

    private fun requirePlatformAdmin(attemptedAction: String): AppUser
    {
        val actor = authTokenContext.authToken.appUser
            ?: throw UnauthorizedException("User is not authenticated")
        if (!userRoleService.isAppAdmin(actor.id))
        {
            authAuditService.emit(
                action = attemptedAction,
                outcome = "DENIED",
                actorId = actor.id,
                reason = "Caller lacks effective App Administrator privilege",
                targetType = "PLATFORM_ORGANIZATION",
            )
            throw UnauthorizedException("User does not have permission to administer platform organizations")
        }
        return actor
    }

    private fun requireOrganization(organizationId: String): Organization
    {
        val id = runCatching { UUID.fromString(organizationId) }
            .getOrElse { throw IllegalArgumentException("Invalid organization ID format") }
        return organizationRepository.findById(id)
            ?: throw OrganizationNotFoundException("Organization not found")
    }

    private fun validatePaging(limit: Int, offset: Int)
    {
        require(limit in 1..100) { "Limit must be between 1 and 100" }
        require(offset >= 0) { "Offset must be greater than or equal to 0" }
    }

    private fun parseStatus(status: String?): Boolean? = when (status?.trim()?.uppercase())
    {
        null, "", "ALL" -> null
        "ACTIVE" -> true
        "INACTIVE" -> false
        else -> throw IllegalArgumentException("Status must be ACTIVE, INACTIVE, or ALL")
    }

    private fun parseSort(sort: String?): String = when (sort?.trim()?.lowercase())
    {
        null, "", "name" -> "name"
        "createddate" -> "createdDate"
        else -> throw IllegalArgumentException("Sort must be name or createdDate")
    }

    private fun parseDirection(direction: String?): String = when (direction?.trim()?.lowercase())
    {
        null, "", "asc" -> "asc"
        "desc" -> "desc"
        else -> throw IllegalArgumentException("Direction must be asc or desc")
    }

    /**
     * The requested codes are validated where they are stored, so only the narrative the
     * administrator attached to this change is checked here.
     */
    private fun validateChangeReason(request: PlatformOrganizationFeatureEntitlementsUpdateRequest)
    {
        require(request.changeReason == null || request.changeReason.length <= MAX_CHANGE_REASON_LENGTH) {
            "Change reason must be at most $MAX_CHANGE_REASON_LENGTH characters"
        }
    }

}
