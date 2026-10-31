package com.docuhyphen.app.api.service.informationrequest.access

import com.docuhyphen.app.api.model.auth.authz.Grant
import com.docuhyphen.app.api.model.entity.InformationRequestDelegatedAuthority
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.model.informationrequest.access.InformationRequestRequirementPolicyFacts
import com.docuhyphen.app.api.repository.informationrequest.party.InformationRequestDelegatedAuthorityRepository
import com.docuhyphen.app.api.service.auth.authz.*
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.sql.Timestamp
import java.time.Instant
import java.util.*

@ApplicationScoped
class InformationRequestDelegatedAuthorityGrantSource @Inject constructor(
    private val authorityRepository: InformationRequestDelegatedAuthorityRepository,
    private val requirementContext: InformationRequestRequirementAuthorizationContextProvider,
) : ResourceGrantSource {
    override val supportedKinds: Set<ResourceKind> =
        setOf(ResourceKind.INFORMATION_REQUEST, ResourceKind.INFORMATION_REQUEST_REQUIREMENT)

    override fun grantsFor(
        principal: PrincipalRef,
        resource: ResourceRef,
        resourceContext: ResourceAuthorizationContext,
    ): List<Grant> =
        when (resource.type) {
            ResourceType.INFORMATION_REQUEST -> requestGrants(principal, resource)
            ResourceType.INFORMATION_REQUEST_REQUIREMENT ->
                (resourceContext.policyFacts as? InformationRequestRequirementPolicyFacts)
                    ?.let { requirementGrants(principal, it) }
                    .orEmpty()

            else -> emptyList()
        }

    private fun requestGrants(principal: PrincipalRef, resource: ResourceRef): List<Grant> {
        val now = Timestamp.from(Instant.now())
        return authorityRepository.findForRequest(resource.id)
            .filter { it.active && !it.effectiveAt.after(now) && (it.expiresAt == null || it.expiresAt!!.after(now)) }
            .filter { principal in requirementContext.principalsActingAs(delegateOf(it)) }
            .map { grantOf(it.id, setOf(Capability.INFORMATION_REQUEST_READ), it.expiresAt) }
    }

    private fun requirementGrants(
        principal: PrincipalRef,
        facts: InformationRequestRequirementPolicyFacts
    ): List<Grant> =
        facts.delegatedAuthorityFacts
            .filter { it.active && it.admits(principal) }
            .mapNotNull { authority ->
                facts.assignedParties.firstOrNull { it.partyId == authority.assignedPartyId }?.let { party ->
                    grantOf(
                        authority.authorityId,
                        RoleCapabilities.forShareRole(ResourceType.INFORMATION_REQUEST, party.roleKey.name),
                        null,
                    )
                }
            }

    private fun delegateOf(authority: InformationRequestDelegatedAuthority) =
        PrincipalRef(authority.delegatePrincipalKind, authority.delegatePrincipalId)

    private fun grantOf(authorityId: UUID, capabilities: Set<Capability>, expiresAt: Timestamp?) =
        Grant(
            sourceKind = Grant.SourceKind.DELEGATED_AUTHORITY,
            sourceId = authorityId,
            roleName = DELEGATE_ROLE,
            capabilities = capabilities,
            expiresAtEpochMillis = expiresAt?.time,
        )

    private companion object {
        const val DELEGATE_ROLE = "DELEGATE"
    }
}
