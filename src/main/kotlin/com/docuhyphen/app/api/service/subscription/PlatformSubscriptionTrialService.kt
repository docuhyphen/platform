package com.docuhyphen.app.api.service.subscription

import com.docuhyphen.app.api.service.auth.AdminApprovalContext
import com.docuhyphen.app.api.service.auth.AuthAuditService
import com.docuhyphen.app.api.service.auth.UserRoleService
import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.interceptor.EnforceAdminAction
import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.resource.model.PlatformSubscriptionTrialExtensionRequest
import com.docuhyphen.app.api.resource.model.PlatformSubscriptionTrialStartRequest
import com.docuhyphen.app.api.service.user.AppUserService
import com.docuhyphen.app.api.service.organization.OrganizationService
import com.docuhyphen.app.api.service.subscription.PlanCode
import com.docuhyphen.app.api.service.subscription.SubscriptionTrialMutation
import com.docuhyphen.app.api.service.subscription.SubscriptionTrialService
import io.quarkus.security.ForbiddenException
import io.quarkus.security.UnauthorizedException
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.time.Instant
import java.util.UUID

@RequestScoped
class PlatformSubscriptionTrialService @Inject constructor(
    private val authTokenContext: AuthTokenContext,
    private val userRoleService: UserRoleService,
    private val authAuditService: AuthAuditService,
    private val appUserService: AppUserService,
    private val organizationService: OrganizationService,
    private val subscriptionTrialService: SubscriptionTrialService,
)
{
    @EnforceAdminAction("PLATFORM_USER_SUBSCRIPTION_TRIAL_START")
    @Transactional
    fun startUserTrial(
        appUserId: String,
        request: PlatformSubscriptionTrialStartRequest,
        adminApprovalContext: AdminApprovalContext,
    ): SubscriptionTrialMutation
    {
        val actor = requirePlatformAdmin("PLATFORM_USER_SUBSCRIPTION_TRIAL_START")
        val user = requireRegisteredUser(appUserId)
        requirePlan(request.planCode, PlanCode.PERSONAL)
        require(request.seatCapacity == null) { "Seat capacity is only valid for an organization trial" }
        val mutation = subscriptionTrialService.startUserTrial(
            appUserId = user.id,
            durationDays = request.durationDays ?: SubscriptionTrialService.DEFAULT_USER_DURATION_DAYS,
            reason = request.reason,
            grantedByAppUserId = actor.id,
        )
        recordRequired("PLATFORM_USER_SUBSCRIPTION_TRIAL_START", actor, mutation, adminApprovalContext)
        return mutation
    }

    @EnforceAdminAction("PLATFORM_USER_SUBSCRIPTION_TRIAL_EXTEND")
    @Transactional
    fun extendUserTrial(
        appUserId: String,
        request: PlatformSubscriptionTrialExtensionRequest,
        adminApprovalContext: AdminApprovalContext,
    ): SubscriptionTrialMutation
    {
        val actor = requirePlatformAdmin("PLATFORM_USER_SUBSCRIPTION_TRIAL_EXTEND")
        val user = requireRegisteredUser(appUserId)
        val mutation = subscriptionTrialService.extendUserTrial(
            appUserId = user.id,
            newPeriodEnd = parsePeriodEnd(request.currentPeriodEnd),
            reason = request.reason,
            grantedByAppUserId = actor.id,
        )
        recordRequired("PLATFORM_USER_SUBSCRIPTION_TRIAL_EXTEND", actor, mutation, adminApprovalContext)
        return mutation
    }

    @EnforceAdminAction("PLATFORM_ORG_SUBSCRIPTION_TRIAL_START")
    @Transactional
    fun startOrganizationTrial(
        organizationId: String,
        request: PlatformSubscriptionTrialStartRequest,
        adminApprovalContext: AdminApprovalContext,
    ): SubscriptionTrialMutation
    {
        val actor = requirePlatformAdmin("PLATFORM_ORG_SUBSCRIPTION_TRIAL_START")
        val organization = requireOrganizationId(organizationId)
        requirePlan(request.planCode, PlanCode.BUSINESS)
        val mutation = subscriptionTrialService.startOrganizationTrial(
            organizationId = organization,
            durationDays = request.durationDays ?: SubscriptionTrialService.DEFAULT_ORGANIZATION_DURATION_DAYS,
            seatCapacity = request.seatCapacity ?: SubscriptionTrialService.DEFAULT_ORGANIZATION_SEAT_CAPACITY,
            reason = request.reason,
            grantedByAppUserId = actor.id,
        )
        recordRequired("PLATFORM_ORG_SUBSCRIPTION_TRIAL_START", actor, mutation, adminApprovalContext)
        return mutation
    }

    @EnforceAdminAction("PLATFORM_ORG_SUBSCRIPTION_TRIAL_EXTEND")
    @Transactional
    fun extendOrganizationTrial(
        organizationId: String,
        request: PlatformSubscriptionTrialExtensionRequest,
        adminApprovalContext: AdminApprovalContext,
    ): SubscriptionTrialMutation
    {
        val actor = requirePlatformAdmin("PLATFORM_ORG_SUBSCRIPTION_TRIAL_EXTEND")
        val organization = requireOrganizationId(organizationId)
        val mutation = subscriptionTrialService.extendOrganizationTrial(
            organizationId = organization,
            newPeriodEnd = parsePeriodEnd(request.currentPeriodEnd),
            reason = request.reason,
            grantedByAppUserId = actor.id,
        )
        recordRequired("PLATFORM_ORG_SUBSCRIPTION_TRIAL_EXTEND", actor, mutation, adminApprovalContext)
        return mutation
    }

    private fun requireRegisteredUser(value: String): AppUser
    {
        val id = parseId(value, "app user ID")
        val user = appUserService.getByIdWithPerson(id)
            ?: throw IllegalArgumentException("App user not found")
        require(!user.isTemporary && user.application == null) {
            "Only registered individual accounts can receive user trials"
        }
        return user
    }

    private fun requireOrganizationId(value: String): UUID
    {
        val id = parseId(value, "organization ID")
        runCatching { organizationService.getOrganizationById(id) }
            .getOrElse { throw IllegalArgumentException("Organization not found") }
        return id
    }

    private fun requirePlatformAdmin(action: String): AppUser
    {
        val actor = authTokenContext.authToken.appUser
            ?: throw UnauthorizedException("User is not authenticated")
        if (!userRoleService.isAppAdmin(actor.id))
        {
            authAuditService.emit(
                action = action,
                outcome = "DENIED",
                actorId = actor.id,
                reason = "Caller lacks effective App Administrator privilege",
                targetType = "SUBSCRIPTION_TRIAL",
            )
            throw ForbiddenException("User does not have permission to manage subscription trials")
        }
        return actor
    }

    private fun recordRequired(
        action: String,
        actor: AppUser,
        mutation: SubscriptionTrialMutation,
        adminApprovalContext: AdminApprovalContext,
    )
    {
        authAuditService.emitRequired(
            action = action,
            outcome = "SUCCESS",
            actorId = actor.id,
            actorRole = "APP_ADMIN",
            requestId = adminApprovalContext.requestId,
            reason = mutation.grant.reason,
            targetType = mutation.ownerType.name,
            targetId = mutation.ownerId.toString(),
            structuredDetails = mapOf(
                "owner_type" to mutation.ownerType.name,
                "owner_id" to mutation.ownerId.toString(),
                "plan_code" to mutation.after.planCode.name,
                "old_status" to mutation.before.status.name,
                "new_status" to mutation.after.status.name,
                "old_period_end" to mutation.before.currentPeriodEnd?.toString().orEmpty(),
                "new_period_end" to mutation.after.currentPeriodEnd?.toString().orEmpty(),
                "actor_id" to actor.id.toString(),
                "grant_id" to mutation.grant.id.toString(),
            ),
        )
    }

    private fun requirePlan(value: String, expected: PlanCode)
    {
        val plan = PlanCode.fromCode(value)
        require(plan == expected) { "${expected.name.lowercase().replaceFirstChar(Char::uppercase)} is the required trial plan" }
    }

    private fun parsePeriodEnd(value: String): Instant
    {
        return runCatching { Instant.parse(value.trim()) }
            .getOrElse { throw IllegalArgumentException("Invalid trial end; use an ISO-8601 UTC timestamp") }
    }

    private fun parseId(value: String, label: String): UUID
    {
        return runCatching { UUID.fromString(value) }
            .getOrElse { throw IllegalArgumentException("Invalid $label format") }
    }
}
