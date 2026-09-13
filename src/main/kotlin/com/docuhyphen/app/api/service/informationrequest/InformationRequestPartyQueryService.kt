package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.InformationRequestPartyDtoMapper
import com.docuhyphen.app.api.model.dto.InformationRequestPartyDto
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestPartyRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.model.entity.InformationRequestParty
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.Decision
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import io.quarkus.security.ForbiddenException
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.UUID

/**
 * Owner- and party-facing reads of a runtime Information Request's parties. Every active party on a
 * request shares the same `INFORMATION_REQUEST_VIEW` capability, so that action alone cannot decide
 * which party rows a caller is shown in full: only a caller holding
 * `INFORMATION_REQUEST_MANAGE_PARTIES`, or the party row that is the caller's own, is shown another
 * party's identity.
 */
@ApplicationScoped
class InformationRequestPartyQueryService @Inject constructor(
    private val requestRepository: InformationRequestRepository,
    private val partyRepository: InformationRequestPartyRepository,
    private val authorizationService: AuthorizationService,
)
{
    fun listForRequest(requestId: UUID, access: RequestAccessContext): List<InformationRequestPartyDto>
    {
        requestRepository.findById(requestId)
            ?: throw IllegalArgumentException("Information Request not found")

        val viewDecision = authorizationService.authorize(
            access.principal,
            Action.INFORMATION_REQUEST_VIEW,
            ResourceRef.informationRequest(requestId),
            access.authorization,
        )
        if (viewDecision is Decision.Deny)
        {
            throw ForbiddenException("Access denied to view Information Request parties")
        }

        val manageDecision = authorizationService.authorize(
            access.principal,
            Action.INFORMATION_REQUEST_MANAGE_PARTIES,
            ResourceRef.informationRequest(requestId),
            access.authorization,
        )
        val isManager = manageDecision !is Decision.Deny

        return partyRepository.findActiveForRequest(requestId).map { party ->
            val revealIdentity = isManager || isOwnParty(party, access.principal)
            InformationRequestPartyDtoMapper.toDto(party, revealIdentity)
        }
    }

    private fun isOwnParty(party: InformationRequestParty, principal: PrincipalRef): Boolean =
        party.principalKind == principal.kind && party.principalId == principal.id
}
