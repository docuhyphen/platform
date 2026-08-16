package com.docuhyphen.app.api.service.auth

import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.interceptor.EnforceAdminAction
import com.docuhyphen.app.api.model.dto.SubscriptionTrialRequestDecisionRequest
import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.model.entity.SubscriptionTrialRequestStatus
import com.docuhyphen.app.api.service.subscription.SubscriptionTrialRequestDecisionService
import com.docuhyphen.app.api.service.subscription.SubscriptionTrialRequestPage
import com.docuhyphen.app.api.service.subscription.SubscriptionTrialRequestService
import com.docuhyphen.app.api.service.subscription.SubscriptionTrialRequestView
import io.quarkus.security.ForbiddenException
import io.quarkus.security.UnauthorizedException
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.util.UUID

@RequestScoped
class PlatformSubscriptionTrialRequestService @Inject constructor(
    private val authTokenContext: AuthTokenContext,
    private val userRoleService: UserRoleService,
    private val requestService: SubscriptionTrialRequestService,
    private val decisionService: SubscriptionTrialRequestDecisionService,
    private val authAuditService: AuthAuditService,
)
{
    fun list(status: String?, limit: Int, offset: Int): SubscriptionTrialRequestPage
    {
        val actor = requirePlatformAdmin("PLATFORM_SUBSCRIPTION_TRIAL_REQUEST_LIST")
        val parsedStatus = status?.trim()?.takeIf(String::isNotEmpty)?.let {
            runCatching { SubscriptionTrialRequestStatus.valueOf(it.uppercase()) }
                .getOrElse { throw IllegalArgumentException("Invalid trial request status") }
        }
        val page = requestService.list(parsedStatus, limit, offset)
        authAuditService.emit(
            action = "PLATFORM_SUBSCRIPTION_TRIAL_REQUEST_LIST",
            outcome = "SUCCESS",
            actorId = actor.id,
            actorRole = "APP_ADMIN",
            targetType = "SUBSCRIPTION_TRIAL_REQUEST",
            reason = "Listed ${parsedStatus?.name ?: "all"} trial requests",
        )
        return page
    }

    @EnforceAdminAction("PLATFORM_SUBSCRIPTION_TRIAL_REQUEST_DECIDE")
    @Transactional
    fun decide(
        requestId: String,
        request: SubscriptionTrialRequestDecisionRequest,
        approval: AdminApprovalContext,
    ): SubscriptionTrialRequestView
    {
        val actor = requirePlatformAdmin("PLATFORM_SUBSCRIPTION_TRIAL_REQUEST_DECIDE")
        val decision = runCatching { SubscriptionTrialRequestStatus.valueOf(request.status.trim().uppercase()) }
            .getOrElse { throw IllegalArgumentException("Trial request status must be APPROVED or REJECTED") }
        val result = decisionService.decide(
            requestId = parseId(requestId),
            decision = decision,
            durationDays = request.durationDays,
            seatCapacity = request.seatCapacity,
            reason = request.reason,
            reviewedByAppUserId = actor.id,
        )
        authAuditService.emitRequired(
            action = "PLATFORM_SUBSCRIPTION_TRIAL_REQUEST_DECIDE",
            outcome = "SUCCESS",
            actorId = actor.id,
            actorRole = "APP_ADMIN",
            requestId = approval.requestId,
            targetType = "SUBSCRIPTION_TRIAL_REQUEST",
            targetId = result.request.id.toString(),
            reason = result.request.decisionReason,
            structuredDetails = mapOf(
                "owner_type" to result.request.ownerType,
                "owner_id" to result.request.ownerId.toString(),
                "plan_code" to result.request.planCode,
                "decision" to result.request.status,
                "trial_grant_id" to result.request.trialGrantId?.toString().orEmpty(),
                "actor_id" to actor.id.toString(),
            ),
        )
        return result
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
                targetType = "SUBSCRIPTION_TRIAL_REQUEST",
            )
            throw ForbiddenException("User does not have permission to manage trial requests")
        }
        return actor
    }

    private fun parseId(value: String): UUID = runCatching { UUID.fromString(value) }
        .getOrElse { throw IllegalArgumentException("Invalid trial request ID") }
}
