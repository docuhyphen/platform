package com.docuhyphen.app.api.service.organization

import com.docuhyphen.app.api.exception.OrganizationTrustAuthorizationException
import com.docuhyphen.app.api.exception.OrganizationTrustRateLimitException
import com.docuhyphen.app.api.exception.OrganizationTrustValidationException
import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.OrganizationTrustDtoTransformer
import com.docuhyphen.app.api.model.dto.OrganizationDirectoryEntryDto
import com.docuhyphen.app.api.model.entity.OrganizationTrustRelationshipStatus
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.service.auth.DirectoryLookupGuardService
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContextFactory
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.Decision
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.config.ConfigurationService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response

@ApplicationScoped
class OrganizationDirectorySearchService @Inject constructor(
    private val organizationService: OrganizationService,
    private val relationshipService: OrganizationTrustRelationshipService,
    private val dtoTransformer: OrganizationTrustDtoTransformer,
    private val directoryLookupGuardService: DirectoryLookupGuardService,
    private val configurationService: ConfigurationService,
    private val authorizationService: AuthorizationService,
    private val authorizationContextFactory: AuthorizationContextFactory,
    private val authTokenContext: AuthTokenContext,
)
{
    fun search(query: String?, requestId: String?): List<OrganizationDirectoryEntryDto>
    {
        val activeOrganizationId = requireActiveOrganization()
        val normalizedQuery = query?.trim()?.lowercase().orEmpty()
        val limitedResponse = directoryLookupGuardService.enforce(
            endpointKey = "organization-trust-discovery",
            targetOrganizationId = activeOrganizationId.toString(),
            requestId = requestId,
            query = normalizedQuery,
        )
        if (limitedResponse != null)
        {
            if (limitedResponse.status == Response.Status.TOO_MANY_REQUESTS.statusCode)
            {
                throw OrganizationTrustRateLimitException()
            }
            throw OrganizationTrustValidationException(
                "Search query must meet the minimum length requirement",
            )
        }

        val currentPartnerIds = relationshipService.listForParty(activeOrganizationId)
            .filter {
                it.status == OrganizationTrustRelationshipStatus.PENDING ||
                    it.status == OrganizationTrustRelationshipStatus.ACTIVE
            }
            .map { it.partnerOf(activeOrganizationId) }
            .toSet()
        return organizationService.searchDiscoverableForTrustRequests(
            activeOrganizationId,
            normalizedQuery,
            configurationService.getDirectoryLookupMaxResults(),
        )
            .filterNot { it.id in currentPartnerIds }
            .map(dtoTransformer::toDirectoryEntryDto)
    }

    private fun requireActiveOrganization(): java.util.UUID
    {
        val organizationId = authTokenContext.activeOrganizationId
            ?: throw OrganizationTrustAuthorizationException("An active organization is required")
        val principal = authorizationContextFactory.currentPrincipal()
            ?: throw OrganizationTrustAuthorizationException("Authentication is required")
        if (principal.kind != PrincipalKind.USER)
        {
            throw OrganizationTrustAuthorizationException("Only an organization member can search organizations")
        }
        val decision = authorizationService.authorize(
            principal,
            Action.ORG_TRUST_REQUEST,
            ResourceRef.organization(organizationId),
            authorizationContextFactory.currentContext(),
        )
        if (decision is Decision.Deny)
        {
            throw OrganizationTrustAuthorizationException()
        }
        return organizationId
    }
}
