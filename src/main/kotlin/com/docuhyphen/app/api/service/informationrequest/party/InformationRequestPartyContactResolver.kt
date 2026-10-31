package com.docuhyphen.app.api.service.informationrequest.party

import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestParty
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.informationrequest.notice.InformationRequestNoticeRecipient
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.exchange.ExternalParticipantService
import com.docuhyphen.app.api.service.exchange.ShareService
import com.docuhyphen.app.api.service.organization.PrincipalGroupService
import com.docuhyphen.app.api.service.user.AppUserService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject

@ApplicationScoped
class InformationRequestPartyContactResolver @Inject constructor(
    private val externalParticipants: ExternalParticipantService,
    private val appUserService: AppUserService,
    private val principalGroups: PrincipalGroupService,
    private val shareService: ShareService,
)
{
    fun emailOf(request: InformationRequest, party: InformationRequestParty): String? =
        when (party.principalKind)
        {
            PrincipalKind.PARTICIPANT -> party.principalId?.let {
                externalParticipants.findOwned(
                    it,
                    InformationRequestParticipantOwner.of(request)
                )?.email
            }

            PrincipalKind.USER -> party.principalId?.let { appUserService.getById(it)?.email }
            else -> null
        }?.trim()?.takeIf { it.isNotBlank() }

    fun recipientsOf(
        request: InformationRequest,
        party: InformationRequestParty
    ): List<InformationRequestNoticeRecipient> {
        val kind = party.principalKind ?: return emptyList()
        val id = party.principalId ?: return emptyList()
        if (kind != PrincipalKind.PRINCIPAL_GROUP) {
            return listOf(InformationRequestNoticeRecipient(PrincipalRef(kind, id), emailOf(request, party)))
        }
        val members = principalGroups.activeMembers(id)
            .map { PrincipalRef(it.principalKind, it.principalId) }
            .filter { it.kind == PrincipalKind.USER || it.kind == PrincipalKind.PARTICIPANT }
            .distinct()
        val heldBack = party.shareId?.let { shareService.membersHeldBackFrom(it, members) }.orEmpty()
        return members
            .filterNot { it in heldBack }
            .map { InformationRequestNoticeRecipient(it, memberEmailOf(it)) }
    }

    private fun memberEmailOf(member: PrincipalRef): String? =
        when (member.kind) {
            PrincipalKind.PARTICIPANT -> externalParticipants.findById(member.id)?.email
            PrincipalKind.USER -> appUserService.getById(member.id)?.email
            else -> null
        }?.trim()?.takeIf { it.isNotBlank() }
}
