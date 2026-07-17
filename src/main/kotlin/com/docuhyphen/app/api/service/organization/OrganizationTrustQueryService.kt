package com.docuhyphen.app.api.service.organization

import com.docuhyphen.app.api.exception.OrganizationTrustAuthorizationException
import com.docuhyphen.app.api.exception.OrganizationTrustNotFoundException
import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.OrganizationTrustDtoTransformer
import com.docuhyphen.app.api.model.dto.OrganizationTrustPoliciesDto
import com.docuhyphen.app.api.model.dto.OrganizationTrustRelationshipDto
import com.docuhyphen.app.api.model.entity.OrganizationTrustRelationship
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContextFactory
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.Decision
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.UUID

@ApplicationScoped
class OrganizationTrustQueryService @Inject constructor(
    private val relationshipService: OrganizationTrustRelationshipService,
    private val policyService: OrganizationTrustPolicyService,
    private val organizationService: OrganizationService,
    private val transformer: OrganizationTrustDtoTransformer,
    private val authorizationService: AuthorizationService,
    private val authorizationContextFactory: AuthorizationContextFactory,
    private val authTokenContext: AuthTokenContext,
)
{
    fun listRelationships(): List<OrganizationTrustRelationshipDto>
    {
        val activeOrganizationId = requireActiveOrganization(Action.ORG_TRUST_VIEW)
        return relationshipService.listForParty(activeOrganizationId).map { relationship ->
            relationshipDto(relationship, activeOrganizationId)
        }
    }

    fun getRelationship(relationshipId: UUID): OrganizationTrustRelationshipDto
    {
        val activeOrganizationId = requireActiveOrganization(Action.ORG_TRUST_VIEW)
        val relationship = relationshipService.getById(relationshipId)
        requireParty(relationship, activeOrganizationId)
        return relationshipDto(relationship, activeOrganizationId)
    }

    fun getPolicies(relationshipId: UUID): OrganizationTrustPoliciesDto
    {
        val activeOrganizationId = requireActiveOrganization(Action.ORG_TRUST_VIEW)
        val relationship = relationshipService.getById(relationshipId)
        requireParty(relationship, activeOrganizationId)
        val policies = policyService.getPolicies(relationshipId).map { policy ->
            val owner = organizationService.getOrganizationById(policy.policyOwnerOrganizationId)
            transformer.toPolicyDto(policy, activeOrganizationId, owner.name)
        }
        return OrganizationTrustPoliciesDto(relationshipId, policies)
    }

    private fun relationshipDto(
        relationship: OrganizationTrustRelationship,
        activeOrganizationId: UUID,
    ): OrganizationTrustRelationshipDto
    {
        val partner = organizationService.getOrganizationById(relationship.partnerOf(activeOrganizationId))
        return transformer.toRelationshipDto(
            relationship,
            activeOrganizationId,
            partner,
            relationshipService.activeSuspensions(relationship.id),
        )
    }

    private fun requireActiveOrganization(action: Action): UUID
    {
        val organizationId = authTokenContext.activeOrganizationId
            ?: throw OrganizationTrustAuthorizationException("An active organization is required")
        val principal = authorizationContextFactory.currentPrincipal()
            ?: throw OrganizationTrustAuthorizationException("Authentication is required")
        if (principal.kind != PrincipalKind.USER)
        {
            throw OrganizationTrustAuthorizationException("Only an organization member can view trust")
        }
        val decision = authorizationService.authorize(
            principal,
            action,
            ResourceRef.organization(organizationId),
            authorizationContextFactory.currentContext(),
        )
        if (decision is Decision.Deny)
        {
            throw OrganizationTrustAuthorizationException()
        }
        return organizationId
    }

    private fun requireParty(relationship: OrganizationTrustRelationship, organizationId: UUID)
    {
        if (!relationship.includes(organizationId))
        {
            throw OrganizationTrustNotFoundException()
        }
    }
}
