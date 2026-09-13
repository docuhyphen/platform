package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.repository.exchange.ExchangeRepository
import com.docuhyphen.app.api.repository.organization.OrganizationMembershipRepository
import com.docuhyphen.app.api.service.fields.AudienceFieldBindingPolicy
import com.docuhyphen.app.api.service.fields.FieldsAccessContext
import com.docuhyphen.app.api.service.fields.FieldsResourceRef
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject

/**
 * The Exchange domain's per-binding Fields policy. It keeps exactly the rules the Exchange has
 * always applied: a caller who reaches the Exchange from outside the ownership that holds it sees
 * and addresses only the bindings classified as public, and a binding the Schema marks read-only is
 * filled by configuration rather than by a caller.
 *
 * An Exchange has no narrower per-binding rule than its audience, so it adds nothing to the shared
 * ones. Which lifecycle states allow editing at all remains the adapter's decision.
 */
@ApplicationScoped
class ExchangeFieldBindingPolicy @Inject constructor(
    private val exchangeRepository: ExchangeRepository,
    private val organizationMembershipRepository: OrganizationMembershipRepository,
) : AudienceFieldBindingPolicy()
{
    /**
     * A caller is external when they reach the Exchange as a recipient rather than as a member of
     * the organization that owns it.
     *
     * - PARTICIPANT / PUBLIC_LINK: always external (magic-link or non-registered participant).
     * - USER: external unless they are an active member of the org that owns the exchange.
     *   For personally-owned exchanges the sole internal user is the owner themselves.
     * - All other principal kinds (SERVICE_ACCOUNT, APPLICATION, etc.) are treated as internal.
     */
    override fun isExternalCaller(resource: FieldsResourceRef, access: FieldsAccessContext): Boolean
    {
        val principal = access.principal
        return when (principal.kind)
        {
            PrincipalKind.PARTICIPANT, PrincipalKind.PUBLIC_LINK -> true
            PrincipalKind.USER ->
            {
                val exchange = exchangeRepository.findById(resource.resourceId) ?: return true
                val ownerOrgId = exchange.ownerOrganizationId
                if (ownerOrgId != null)
                {
                    organizationMembershipRepository.findActiveByUserAndOrg(principal.id, ownerOrgId) == null
                }
                else
                {
                    // Personally-owned exchange: only the owning user is internal.
                    exchange.ownerUserId != principal.id
                }
            }
            else -> false
        }
    }
}
