package com.docuhyphen.app.api.service.subscription

import com.docuhyphen.app.api.service.auth.AuthAuditService
import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.dto.SubscriptionTrialRequestCreateRequest
import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContextFactory
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.Decision
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.subscription.CurrentSubscriptionTrialRequest
import com.docuhyphen.app.api.service.subscription.SubscriptionOwnerType
import com.docuhyphen.app.api.service.subscription.SubscriptionTrialRequestService
import com.docuhyphen.app.api.service.subscription.SubscriptionTrialRequestView
import io.quarkus.security.ForbiddenException
import io.quarkus.security.UnauthorizedException
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional

@RequestScoped
class SelfServiceSubscriptionTrialRequestService @Inject constructor(
    private val authTokenContext: AuthTokenContext,
    private val authorizationService: AuthorizationService,
    private val authorizationContextFactory: AuthorizationContextFactory,
    private val requestService: SubscriptionTrialRequestService,
    private val authAuditService: AuthAuditService,
)
{
    @Transactional
    fun create(request: SubscriptionTrialRequestCreateRequest): SubscriptionTrialRequestView
    {
        val caller = requireRegisteredUser()
        val owner = currentOwner()
        val result = requestService.create(owner.first, owner.second, caller.id, request.note)
        authAuditService.emit(
            action = "SUBSCRIPTION_TRIAL_REQUEST_CREATE",
            outcome = "SUCCESS",
            actorId = caller.id,
            targetType = owner.first.name,
            targetId = owner.second.toString(),
            reason = request.note ?: "Trial requested from Billing",
        )
        return result
    }

    fun current(): CurrentSubscriptionTrialRequest
    {
        requireRegisteredUser()
        val owner = currentOwner()
        return requestService.current(owner.first, owner.second)
    }

    private fun currentOwner(): Pair<SubscriptionOwnerType, java.util.UUID>
    {
        val organizationId = authTokenContext.activeOrganizationId
            ?: return SubscriptionOwnerType.USER to requireRegisteredUser().id
        val principal = authorizationContextFactory.currentPrincipal()
            ?: throw UnauthorizedException("Authentication is required")
        if (principal.kind != PrincipalKind.USER)
        {
            throw ForbiddenException("Only users can request subscription trials")
        }
        val decision = authorizationService.authorize(
            principal,
            Action.ORG_MANAGE_BILLING,
            ResourceRef.organization(organizationId),
            authorizationContextFactory.currentContext(),
        )
        if (decision is Decision.Deny)
        {
            throw ForbiddenException("Organization billing permission is required")
        }
        return SubscriptionOwnerType.ORGANIZATION to organizationId
    }

    private fun requireRegisteredUser(): AppUser
    {
        val user = authTokenContext.authToken.appUser
            ?: throw UnauthorizedException("Authentication is required")
        if (user.isTemporary || user.application != null)
        {
            throw ForbiddenException("Only registered users can request subscription trials")
        }
        return user
    }
}
