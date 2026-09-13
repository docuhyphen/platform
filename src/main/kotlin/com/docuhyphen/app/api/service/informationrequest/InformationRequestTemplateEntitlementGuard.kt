package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequestTemplateScopeKind
import com.docuhyphen.app.api.service.subscription.PlanFeature
import com.docuhyphen.app.api.service.subscription.SubscriptionAccessService
import com.docuhyphen.app.api.service.subscription.SubscriptionContext
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.UUID

/**
 * Applies the two commercial and operational decisions about Information Requests to reusable
 * Template configuration.
 *
 * Both are decisions about the owner the configuration names, not about the caller acting on it and
 * not about whichever organization the caller happens to have selected. An organization-owned
 * Template is answered for by that organization and a personally owned one by that person, so each
 * scope kind consults exactly the owner it names and never the one it does not.
 *
 * Neither decision is softened by the subscription enforcement mode, because
 * [PlanFeature.INFORMATION_REQUESTS] requires an explicit release grant and a capability still being
 * built has to stay closed in every environment. The mutation allowance is separate and does answer
 * to that mode, since it is an ordinary commercial state rather than a readiness statement.
 */
@ApplicationScoped
class InformationRequestTemplateEntitlementGuard @Inject constructor(
    private val subscriptionAccessService: SubscriptionAccessService,
)
{
    /** Reads answer to both gates too, so an unreleased capability exposes no configuration. */
    fun requireTemplateAccess(
        scopeKind: InformationRequestTemplateScopeKind,
        organizationId: UUID?,
        userId: UUID?,
    )
    {
        subscriptionAccessService.requireFeature(
            owner(scopeKind, organizationId, userId),
            PlanFeature.INFORMATION_REQUESTS,
        )
    }

    fun requireTemplateMutation(
        scopeKind: InformationRequestTemplateScopeKind,
        organizationId: UUID?,
        userId: UUID?,
    )
    {
        val owner = owner(scopeKind, organizationId, userId)
        subscriptionAccessService.requireMutationAllowed(owner)
        subscriptionAccessService.requireFeature(owner, PlanFeature.INFORMATION_REQUESTS)
    }

    /**
     * @throws InformationRequestTemplateValidationException for a scope with no owner to answer for
     * it. The platform is storable as a Template owner but is not a paying subject and holds no
     * release grant, so a platform-owned Template would reach neither gate and would leave the
     * capability open in exactly the deployments it is meant to be closed in.
     */
    private fun owner(
        scopeKind: InformationRequestTemplateScopeKind,
        organizationId: UUID?,
        userId: UUID?,
    ): SubscriptionContext = when (scopeKind)
    {
        InformationRequestTemplateScopeKind.PLATFORM -> throw
            InformationRequestTemplateValidationException(
                "A PLATFORM information request template has no owner to hold the entitlement and " +
                    "release decisions this capability answers to",
            )
        InformationRequestTemplateScopeKind.ORGANIZATION -> SubscriptionContext.forOrganization(
            requireNotNull(organizationId) {
                "An organization-owned information request template names no organization"
            },
        )
        InformationRequestTemplateScopeKind.PERSONAL -> SubscriptionContext.forUser(
            requireNotNull(userId) {
                "A personally owned information request template names no person"
            },
        )
    }
}
