package com.docuhyphen.app.api.service.auth

import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.interceptor.EnforceAdminAction
import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.resource.model.PlatformSubscriptionTrialConversionRequest
import com.docuhyphen.app.api.resource.model.PlatformSubscriptionTrialEndRequest
import com.docuhyphen.app.api.service.AppUserService
import com.docuhyphen.app.api.service.organization.OrganizationService
import com.docuhyphen.app.api.service.subscription.BillingFrequency
import com.docuhyphen.app.api.service.subscription.SubscriptionTrialTransition
import com.docuhyphen.app.api.service.subscription.SubscriptionTrialTransitionService
import io.quarkus.security.ForbiddenException
import io.quarkus.security.UnauthorizedException
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.time.Instant
import java.util.UUID

@RequestScoped
class PlatformSubscriptionTrialTransitionService @Inject constructor(
    private val authTokenContext: AuthTokenContext,
    private val userRoleService: UserRoleService,
    private val authAuditService: AuthAuditService,
    private val appUserService: AppUserService,
    private val organizationService: OrganizationService,
    private val transitionService: SubscriptionTrialTransitionService,
)
{
    @EnforceAdminAction("PLATFORM_USER_SUBSCRIPTION_TRIAL_END")
    @Transactional
    fun endUserTrial(
        appUserId: String,
        request: PlatformSubscriptionTrialEndRequest,
        approval: AdminApprovalContext,
    ): SubscriptionTrialTransition
    {
        val action = "PLATFORM_USER_SUBSCRIPTION_TRIAL_END"
        val actor = requirePlatformAdmin(action)
        val user = requireRegisteredUser(appUserId)
        val result = transitionService.endUserTrial(user.id, request.reason)
        recordRequired(action, actor, result, approval)
        return result
    }

    @EnforceAdminAction("PLATFORM_ORG_SUBSCRIPTION_TRIAL_END")
    @Transactional
    fun endOrganizationTrial(
        organizationId: String,
        request: PlatformSubscriptionTrialEndRequest,
        approval: AdminApprovalContext,
    ): SubscriptionTrialTransition
    {
        val action = "PLATFORM_ORG_SUBSCRIPTION_TRIAL_END"
        val actor = requirePlatformAdmin(action)
        val result = transitionService.endOrganizationTrial(requireOrganizationId(organizationId), request.reason)
        recordRequired(action, actor, result, approval)
        return result
    }

    @EnforceAdminAction("PLATFORM_USER_SUBSCRIPTION_TRIAL_CONVERT")
    @Transactional
    fun convertUserTrial(
        appUserId: String,
        request: PlatformSubscriptionTrialConversionRequest,
        approval: AdminApprovalContext,
    ): SubscriptionTrialTransition
    {
        val action = "PLATFORM_USER_SUBSCRIPTION_TRIAL_CONVERT"
        val actor = requirePlatformAdmin(action)
        val user = requireRegisteredUser(appUserId)
        require(request.seatCapacity == null) { "Seat capacity is only valid for an organization conversion" }
        val result = transitionService.convertUserTrial(
            user.id,
            parseBillingFrequency(request.billingFrequency),
            parsePeriodEnd(request.currentPeriodEnd),
            request.reason,
        )
        recordRequired(action, actor, result, approval)
        return result
    }

    @EnforceAdminAction("PLATFORM_ORG_SUBSCRIPTION_TRIAL_CONVERT")
    @Transactional
    fun convertOrganizationTrial(
        organizationId: String,
        request: PlatformSubscriptionTrialConversionRequest,
        approval: AdminApprovalContext,
    ): SubscriptionTrialTransition
    {
        val action = "PLATFORM_ORG_SUBSCRIPTION_TRIAL_CONVERT"
        val actor = requirePlatformAdmin(action)
        val seatCapacity = request.seatCapacity
            ?: throw IllegalArgumentException("Purchased seat capacity is required")
        val result = transitionService.convertOrganizationTrial(
            requireOrganizationId(organizationId),
            parseBillingFrequency(request.billingFrequency),
            parsePeriodEnd(request.currentPeriodEnd),
            seatCapacity,
            request.reason,
        )
        recordRequired(action, actor, result, approval)
        return result
    }

    private fun requireRegisteredUser(value: String): AppUser
    {
        val user = appUserService.getByIdWithPerson(parseId(value, "app user ID"))
            ?: throw IllegalArgumentException("App user not found")
        require(!user.isTemporary && user.application == null) {
            "Only registered individual accounts can hold user subscriptions"
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
        transition: SubscriptionTrialTransition,
        approval: AdminApprovalContext,
    )
    {
        authAuditService.emitRequired(
            action = action,
            outcome = "SUCCESS",
            actorId = actor.id,
            actorRole = "APP_ADMIN",
            requestId = approval.requestId,
            reason = transition.reason,
            targetType = transition.ownerType.name,
            targetId = transition.ownerId.toString(),
            structuredDetails = mapOf(
                "owner_type" to transition.ownerType.name,
                "owner_id" to transition.ownerId.toString(),
                "old_plan_code" to transition.before.planCode.name,
                "new_plan_code" to transition.after.planCode.name,
                "old_status" to transition.before.status.name,
                "new_status" to transition.after.status.name,
                "old_period_end" to transition.before.currentPeriodEnd?.toString().orEmpty(),
                "new_period_end" to transition.after.currentPeriodEnd?.toString().orEmpty(),
                "old_billing_frequency" to transition.before.billingFrequency?.name.orEmpty(),
                "new_billing_frequency" to transition.after.billingFrequency?.name.orEmpty(),
                "old_seat_capacity" to transition.before.seatCapacity?.toString().orEmpty(),
                "new_seat_capacity" to transition.after.seatCapacity?.toString().orEmpty(),
                "actor_id" to actor.id.toString(),
            ),
        )
    }

    private fun parseBillingFrequency(value: String): BillingFrequency
    {
        return BillingFrequency.fromCodeOrNull(value)
            ?: throw IllegalArgumentException("Billing frequency must be MONTHLY or ANNUAL")
    }

    private fun parsePeriodEnd(value: String): Instant
    {
        return runCatching { Instant.parse(value.trim()) }
            .getOrElse { throw IllegalArgumentException("Invalid paid period end; use an ISO-8601 UTC timestamp") }
    }

    private fun parseId(value: String, label: String): UUID
    {
        return runCatching { UUID.fromString(value) }
            .getOrElse { throw IllegalArgumentException("Invalid $label format") }
    }
}
