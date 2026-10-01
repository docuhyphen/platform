package com.docuhyphen.app.api.service.informationrequest.party

import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestOwnerType
import com.docuhyphen.app.api.model.entity.InformationRequestParty
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.service.exchange.ExternalParticipantOwner
import com.docuhyphen.app.api.service.exchange.ExternalParticipantService
import com.docuhyphen.app.api.service.user.AppUserService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject

@ApplicationScoped
class InformationRequestPartyContactResolver @Inject constructor(
    private val externalParticipants: ExternalParticipantService,
    private val appUserService: AppUserService,
)
{
    fun emailOf(request: InformationRequest, party: InformationRequestParty): String? =
        when (party.principalKind)
        {
            PrincipalKind.PARTICIPANT -> party.principalId?.let {
                externalParticipants.findOwned(
                    it,
                    participantOwnerOf(request)
                )?.email
            }

            PrincipalKind.USER -> party.principalId?.let { appUserService.getById(it)?.email }
            else -> null
        }?.trim()?.takeIf { it.isNotBlank() }

    private fun participantOwnerOf(request: InformationRequest): ExternalParticipantOwner = when (request.ownerType)
    {
        InformationRequestOwnerType.ORGANIZATION -> ExternalParticipantOwner.Organization(requireNotNull(request.ownerOrganizationId))
        InformationRequestOwnerType.USER -> ExternalParticipantOwner.Personal(requireNotNull(request.ownerUserId))
    }
}
