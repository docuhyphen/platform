package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.model.entity.ExchangeStatus
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.repository.ExchangeRepository
import com.docuhyphen.app.api.repository.OrganizationMembershipRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.Decision
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.auth.authz.ScopeReference
import com.docuhyphen.app.api.service.fields.FieldResourceAdapter
import io.quarkus.security.ForbiddenException
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.UUID

/**
 * The Exchange domain's implementation of the Fields [FieldResourceAdapter] port. Encapsulates
 * Exchange existence, ownership, authorization (via [AuthorizationService]), and the lifecycle rule
 * that field values may only be edited while an Exchange is INITIATED (Draft) in the first release.
 * The Fields engine depends on this port, never on [ExchangeRepository] directly.
 */
@ApplicationScoped
class ExchangeFieldResourceAdapter @Inject constructor(
    private val exchangeRepository: ExchangeRepository,
    private val authorizationService: AuthorizationService,
    private val organizationMembershipRepository: OrganizationMembershipRepository,
) : FieldResourceAdapter
{
    override val resourceType: String = "EXCHANGE"

    override fun exists(resourceId: UUID): Boolean =
        exchangeRepository.findById(resourceId)?.isDeleted == false

    override fun ownerScope(resourceId: UUID): ScopeReference?
    {
        val exchange = exchangeRepository.findById(resourceId) ?: return null
        // Organization-owned exchanges resolve to their org configuration scope. Personal-owned
        // exchanges have no organization scope; only platform schemas could apply to them.
        return exchange.ownerOrganizationId?.let { ScopeReference.Organization(it) }
    }

    override fun authorizeViewFields(
        resourceId: UUID,
        principal: PrincipalRef,
        context: AuthorizationContext,
    )
    {
        val decision = authorizationService.authorize(
            principal, Action.EXCHANGE_VIEW, ResourceRef.exchange(resourceId), context,
        )
        if (decision is Decision.Deny) throw ForbiddenException("Access denied to exchange fields")
    }

    override fun authorizeManageFields(
        resourceId: UUID,
        principal: PrincipalRef,
        context: AuthorizationContext,
    )
    {
        val decision = authorizationService.authorize(
            principal, Action.EXCHANGE_EDIT, ResourceRef.exchange(resourceId), context,
        )
        if (decision is Decision.Deny) throw ForbiddenException("Access denied to manage exchange fields")
    }

    override fun valuesEditable(resourceId: UUID): Boolean
    {
        val exchange = exchangeRepository.findById(resourceId) ?: return false
        return !exchange.isDeleted && exchange.status == ExchangeStatus.INITIATED
    }

    /**
     * A caller is external when they access the exchange as a recipient rather than as a member
     * of the org that owns it.
     *
     * - PARTICIPANT / PUBLIC_LINK: always external (magic-link or non-registered participant).
     * - USER: external unless they are an active member of the org that owns the exchange.
     *   For personally-owned exchanges the sole internal user is the owner themselves.
     * - All other principal kinds (SERVICE_ACCOUNT, APPLICATION, etc.) are treated as internal.
     */
    override fun isExternalCaller(
        resourceId: UUID,
        principal: PrincipalRef,
        context: AuthorizationContext,
    ): Boolean
    {
        return when (principal.kind)
        {
            PrincipalKind.PARTICIPANT, PrincipalKind.PUBLIC_LINK -> true
            PrincipalKind.USER ->
            {
                val exchange = exchangeRepository.findById(resourceId) ?: return true
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
