package com.docuhyphen.app.api.service.subscription

import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.interceptor.EnforceAdminAction
import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.model.entity.SubscriptionFeatureEntitlement
import com.docuhyphen.app.api.resource.model.PlatformUserFeatureEntitlementsUpdateRequest
import com.docuhyphen.app.api.service.auth.AdminApprovalContext
import com.docuhyphen.app.api.service.auth.AuthAuditService
import com.docuhyphen.app.api.service.auth.UserRoleService
import com.docuhyphen.app.api.service.user.AppUserService
import io.quarkus.security.ForbiddenException
import io.quarkus.security.UnauthorizedException
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.util.UUID

/** An individual account and the platform-administered feature decisions it holds. */
data class UserFeatureEntitlementResult(
    val user: AppUser,
    val entitlements: List<SubscriptionFeatureEntitlement>,
)

/**
 * Platform administration of the feature decisions recorded against an individual account: the
 * personal counterpart of the organization surface. An App Administrator can add a feature the
 * account's plan omits or withdraw one it grants, which is what an organization owner could already
 * be decided about.
 *
 * Only a registered individual account can hold a decision. A temporary recipient and a registered
 * application both exist without a subscription of their own, so there is no commercial position for
 * a decision to change.
 */
@RequestScoped
class PlatformUserFeatureEntitlementService @Inject constructor(
    private val authTokenContext: AuthTokenContext,
    private val userRoleService: UserRoleService,
    private val authAuditService: AuthAuditService,
    private val appUserService: AppUserService,
    private val featureEntitlementAdminService: SubscriptionFeatureEntitlementAdminService,
)
{
    companion object
    {
        private const val MAX_CHANGE_REASON_LENGTH = 1024
        private const val TARGET_TYPE = "APP_USER"
    }

    fun get(appUserId: String, requestId: String?): UserFeatureEntitlementResult
    {
        val actor = requirePlatformAdmin("PLATFORM_USER_FEATURE_ENTITLEMENTS_VIEW")
        val account = requireIndividualAccount(appUserId)
        val entitlements = featureEntitlementAdminService.findDecisions(
            SubscriptionContext.forUser(account.id),
        )
        authAuditService.emit(
            action = "PLATFORM_USER_FEATURE_ENTITLEMENTS_VIEW",
            outcome = "SUCCESS",
            actorId = actor.id,
            requestId = requestId,
            reason = "Platform administrator viewed individual account feature entitlements",
            targetType = TARGET_TYPE,
            targetId = account.id.toString(),
        )
        return UserFeatureEntitlementResult(account, entitlements)
    }

    @EnforceAdminAction("PLATFORM_USER_FEATURE_ENTITLEMENTS_UPDATE")
    @Transactional
    fun replace(
        appUserId: String,
        request: PlatformUserFeatureEntitlementsUpdateRequest,
        adminApprovalContext: AdminApprovalContext,
    ): UserFeatureEntitlementResult
    {
        val actor = requirePlatformAdmin("PLATFORM_USER_FEATURE_ENTITLEMENTS_UPDATE")
        val account = requireIndividualAccount(appUserId)
        val changeReason = validatedChangeReason(request.changeReason)

        val replacement = featureEntitlementAdminService.replaceDecisions(
            owner = SubscriptionContext.forUser(account.id),
            requested = request.entitlements.map {
                FeatureEntitlementDecision(it.featureCode, it.enabled)
            },
            actorId = actor.id,
        )

        authAuditService.emitRequired(
            action = "PLATFORM_USER_FEATURE_ENTITLEMENTS_UPDATE",
            outcome = "SUCCESS",
            actorId = actor.id,
            actorRole = "APP_ADMIN",
            requestId = adminApprovalContext.requestId,
            reason = changeReason
                ?: "Platform administrator replaced individual account feature entitlements",
            beforeSnapshot = replacement.beforeSnapshot,
            afterSnapshot = replacement.afterSnapshot,
            targetType = TARGET_TYPE,
            targetId = account.id.toString(),
            structuredDetails = mapOf(
                "before_state" to replacement.beforeSnapshot,
                "after_state" to replacement.afterSnapshot,
            ),
        )
        return UserFeatureEntitlementResult(account, replacement.entitlements)
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
                targetType = TARGET_TYPE,
            )
            throw ForbiddenException(
                "User does not have permission to administer individual account feature entitlements",
            )
        }
        return actor
    }

    private fun requireIndividualAccount(value: String): AppUser
    {
        val id = runCatching { UUID.fromString(value) }
            .getOrElse { throw IllegalArgumentException("Invalid app user ID format") }
        val account = appUserService.getByIdWithPerson(id)
            ?: throw IllegalArgumentException("App user not found")
        require(!account.isTemporary && account.application == null) {
            "Only registered individual accounts can hold feature entitlements"
        }
        return account
    }

    private fun validatedChangeReason(changeReason: String?): String?
    {
        require(changeReason == null || changeReason.length <= MAX_CHANGE_REASON_LENGTH) {
            "Change reason must be at most $MAX_CHANGE_REASON_LENGTH characters"
        }
        return changeReason?.trim()?.takeIf { it.isNotBlank() }
    }
}
