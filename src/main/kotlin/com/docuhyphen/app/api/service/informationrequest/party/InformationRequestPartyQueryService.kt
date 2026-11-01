package com.docuhyphen.app.api.service.informationrequest.party

import com.docuhyphen.app.api.model.InformationRequestPartyDtoMapper
import com.docuhyphen.app.api.model.auth.authz.Decision
import com.docuhyphen.app.api.model.entity.InformationRequestParty
import com.docuhyphen.app.api.model.entity.InformationRequestShareRoleKey
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.party.InformationRequestPartyListing
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.party.InformationRequestPartyRepository
import com.docuhyphen.app.api.repository.informationrequest.party.SubjectIdentityExternalIdentifierRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.model.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.exchange.ExchangeRecipientService
import com.docuhyphen.app.api.service.identity.PrincipalDisplayService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestReadAuthorization
import com.docuhyphen.app.api.service.informationrequest.privacy.InformationRequestSubjectRestrictionService
import io.quarkus.security.ForbiddenException
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.*

@ApplicationScoped
class InformationRequestPartyQueryService @Inject constructor(
    private val requestRepository: InformationRequestRepository,
    private val partyRepository: InformationRequestPartyRepository,
    private val authorizationService: AuthorizationService,
    private val principalDisplayService: PrincipalDisplayService,
    private val identifierRepository: SubjectIdentityExternalIdentifierRepository,
    private val exchangeRecipientService: ExchangeRecipientService,
    private val restrictions: InformationRequestSubjectRestrictionService,
)
{
    private fun visibleParties(
        requestId: UUID,
        access: RequestAccessContext
    ): List<Pair<InformationRequestParty, Boolean>>
    {
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
            Action.INFORMATION_REQUEST_VIEW_OPERATIONS,
            ResourceRef.informationRequest(requestId),
            access.authorization,
        )
        val isManager = manageDecision !is Decision.Deny

        return partyRepository.findActiveForRequest(requestId).map { party ->
            val revealIdentity = isManager || isOwnParty(party, access.principal)
            party to revealIdentity
        }
    }

    fun listForManagement(requestId: UUID, access: RequestAccessContext): InformationRequestPartyListing
    {
        InformationRequestReadAuthorization.requireVisibleTarget(authorizationService, requestId, access)
        val request = requestRepository.findById(requestId)
            ?: throw IllegalArgumentException("Information Request not found")
        val listed = visibleParties(requestId, access)
        val ownerId = request.ownerOrganizationId ?: request.ownerUserId
        val subjectReferences = identifierRepository
            .findForSubjects(listed.mapNotNull { (party, revealIdentity) -> party.subjectIdentityRefId.takeIf { revealIdentity } })
            .groupBy { it.subjectIdentityRefId }
        val parties = listed.map { (party, revealIdentity) ->
            val principalId = party.principalId
            val principalKind = party.principalKind
            val subjectId = party.subjectIdentityRefId
            val label = when
            {
                !revealIdentity -> null
                principalId != null && principalKind != null ->
                    principalDisplayService.display(PrincipalRef(principalKind, principalId)).name

                subjectId != null ->
                    subjectReferences[subjectId]?.firstOrNull()
                        ?.let { "${it.identifierType} ${it.identifierValue}" }

                else -> null
            }
            InformationRequestPartyDtoMapper.toDto(
                party, revealIdentity, label,
                revealIdentity && party.exchangeRecipientId?.let(exchangeRecipientService::trustSuspended) == true,
                revealIdentity && party.roleKey == InformationRequestShareRoleKey.SUBJECT && subjectId != null &&
                        ownerId != null && restrictions.isRestricted(request.ownerType, ownerId, subjectId),
            )
        }
        return InformationRequestPartyListing(parties, InformationRequestETag.partiesOf(request))
    }

    fun partiesOf(requestId: UUID): List<InformationRequestParty> = partyRepository.findForRequest(requestId)

    fun findParty(partyId: UUID): InformationRequestParty? = partyRepository.findById(partyId)

    private fun isOwnParty(party: InformationRequestParty, principal: PrincipalRef): Boolean =
        party.principalKind == principal.kind && party.principalId == principal.id
}
