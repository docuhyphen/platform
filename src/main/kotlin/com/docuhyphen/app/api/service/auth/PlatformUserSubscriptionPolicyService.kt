package com.docuhyphen.app.api.service.auth

import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.interceptor.EnforceAdminAction
import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.model.entity.UserSubscriptionPolicy
import com.docuhyphen.app.api.resource.model.PlatformUserSubscriptionPolicyRequest
import com.docuhyphen.app.api.service.AppUserService
import com.docuhyphen.app.api.service.subscription.BillingFrequency
import com.docuhyphen.app.api.service.subscription.PlanCode
import com.docuhyphen.app.api.service.subscription.SubscriptionLifecycleUpdate
import com.docuhyphen.app.api.service.subscription.SubscriptionLifecycleValidator
import com.docuhyphen.app.api.service.subscription.SubscriptionOwnerType
import com.docuhyphen.app.api.service.subscription.SubscriptionPolicyService
import com.docuhyphen.app.api.service.subscription.SubscriptionStatus
import io.quarkus.security.UnauthorizedException
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

data class UserSubscriptionPolicyResult(
    val user: AppUser,
    val policy: UserSubscriptionPolicy,
)

data class UserSubscriptionPolicyListResult(
    val total: Int,
    val limit: Int,
    val offset: Int,
    val items: List<UserSubscriptionPolicyResult>,
)

@RequestScoped
class PlatformUserSubscriptionPolicyService @Inject constructor(
    private val authTokenContext: AuthTokenContext,
    private val userRoleService: UserRoleService,
    private val authAuditService: AuthAuditService,
    private val appUserService: AppUserService,
    private val subscriptionPolicyService: SubscriptionPolicyService,
    private val lifecycleValidator: SubscriptionLifecycleValidator,
)
{
    fun list(query: String?, limit: Int, offset: Int, requestId: String?): UserSubscriptionPolicyListResult
    {
        val actor = requirePlatformAdmin("PLATFORM_USER_SUBSCRIPTION_POLICY_LIST")
        require(limit in 1..100) { "Limit must be between 1 and 100" }
        require(offset >= 0) { "Offset must be greater than or equal to 0" }
        require(query == null || query.length <= 100) { "Search query must be at most 100 characters" }

        val users = appUserService.findForSubscriptionAdministration(query, limit, offset)
        val policies = subscriptionPolicyService.findUserPolicies(users.map { it.id })
            .associateBy { it.appUserId }
        val items = users.map { user ->
            UserSubscriptionPolicyResult(
                user = user,
                policy = policies[user.id] ?: subscriptionPolicyService.ensureUserPolicy(user.id),
            )
        }
        val total = appUserService.countForSubscriptionAdministration(query).toInt()
        authAuditService.emit(
            action = "PLATFORM_USER_SUBSCRIPTION_POLICY_LIST",
            outcome = "SUCCESS",
            actorId = actor.id,
            requestId = requestId,
            reason = "Platform administrator listed user subscription policies",
            structuredDetails = mapOf("total" to total.toString(), "returned" to items.size.toString()),
        )
        return UserSubscriptionPolicyListResult(total, limit, offset, items)
    }

    fun get(appUserId: String, requestId: String?): UserSubscriptionPolicyResult
    {
        val actor = requirePlatformAdmin("PLATFORM_USER_SUBSCRIPTION_POLICY_VIEW")
        val user = requireRegisteredUser(appUserId)
        val policy = subscriptionPolicyService.findUserPolicy(user.id)
            ?: subscriptionPolicyService.ensureUserPolicy(user.id)
        authAuditService.emit(
            action = "PLATFORM_USER_SUBSCRIPTION_POLICY_VIEW",
            outcome = "SUCCESS",
            actorId = actor.id,
            requestId = requestId,
            reason = "Platform administrator viewed a user subscription policy",
            targetType = "APP_USER",
            targetId = user.id.toString(),
        )
        return UserSubscriptionPolicyResult(user, policy)
    }

    @EnforceAdminAction("PLATFORM_USER_SUBSCRIPTION_POLICY_UPDATE")
    @Transactional
    fun update(
        appUserId: String,
        request: PlatformUserSubscriptionPolicyRequest,
        adminApprovalContext: AdminApprovalContext,
    ): UserSubscriptionPolicyResult
    {
        val actor = requirePlatformAdmin("PLATFORM_USER_SUBSCRIPTION_POLICY_UPDATE")
        val user = requireRegisteredUser(appUserId)
        val policy = subscriptionPolicyService.findUserPolicyForUpdate(user.id)
            ?: subscriptionPolicyService.ensureUserPolicy(user.id)
        val update = lifecycleUpdate(request)
        lifecycleValidator.validate(SubscriptionOwnerType.USER, update)
        val before = snapshot(policy)
        val now = Timestamp.from(Instant.now())

        policy.planCode = update.planCode.name
        policy.subscriptionStatus = update.status.name
        policy.billingFrequency = update.billingFrequency?.name
        policy.currentPeriodStart = update.currentPeriodStart?.let(Timestamp::from)
        policy.currentPeriodEnd = update.currentPeriodEnd?.let(Timestamp::from)
        policy.gracePeriodEnd = update.gracePeriodEnd?.let(Timestamp::from)
        policy.changeReason = update.changeReason
        policy.updatedDate = now
        subscriptionPolicyService.updateUserPolicy(policy)

        val after = snapshot(policy)
        authAuditService.emitRequired(
            action = "PLATFORM_USER_SUBSCRIPTION_POLICY_UPDATE",
            outcome = "SUCCESS",
            actorId = actor.id,
            actorRole = "APP_ADMIN",
            requestId = adminApprovalContext.requestId,
            reason = update.changeReason,
            targetType = "APP_USER",
            targetId = user.id.toString(),
            structuredDetails = mapOf("before_state" to before, "after_state" to after),
        )
        return UserSubscriptionPolicyResult(user, policy)
    }

    private fun lifecycleUpdate(request: PlatformUserSubscriptionPolicyRequest): SubscriptionLifecycleUpdate
    {
        val planCode = PlanCode.fromCodeOrNull(request.planCode)
            ?: throw IllegalArgumentException("Unknown subscription plan code: ${request.planCode}")
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

    private fun requireRegisteredUser(value: String): AppUser
    {
        val id = runCatching { UUID.fromString(value) }
            .getOrElse { throw IllegalArgumentException("Invalid app user ID format") }
        val user = appUserService.getByIdWithPerson(id) ?: throw IllegalArgumentException("App user not found")
        require(!user.isTemporary && user.application == null) {
            "Only registered individual accounts can hold user subscriptions"
        }
        return user
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
                targetType = "PLATFORM_USER_SUBSCRIPTION",
            )
            throw UnauthorizedException("User does not have permission to manage user subscription policies")
        }
        return actor
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

    private fun snapshot(policy: UserSubscriptionPolicy): String
    {
        return "appUserId=${policy.appUserId};planCode=${policy.planCode};subscriptionStatus=${policy.subscriptionStatus};billingFrequency=${policy.billingFrequency};currentPeriodStart=${policy.currentPeriodStart};currentPeriodEnd=${policy.currentPeriodEnd};gracePeriodEnd=${policy.gracePeriodEnd};changeReason=${policy.changeReason};updatedDate=${policy.updatedDate}"
    }
}
