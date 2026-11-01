package com.docuhyphen.app.api.model.informationrequest.party

import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.dto.InformationRequestPartyDto
import com.docuhyphen.app.api.model.entity.InformationRequestParty
import com.docuhyphen.app.api.model.entity.InformationRequestShareRoleKey
import com.docuhyphen.app.api.model.entity.ShareLink
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import java.util.*

data class InformationRequestAccessLinkView(
    val shareLink: ShareLink,
    val partyId: UUID,
)

data class InformationRequestPartyListing(
    val parties: List<InformationRequestPartyDto>,
    val partiesETag: String,
)

data class AssignInformationRequestPartyCommand(
    val requestId: UUID,
    val roleKey: InformationRequestShareRoleKey,
    val principal: PrincipalRef? = null,
    val subjectIdentityRefId: UUID? = null,
    val exchangeRecipientId: UUID? = null,
    val access: RequestAccessContext,
    val precondition: CommandPrecondition,
    val idempotencyKey: String,
)

data class AssignExternalParticipantInformationRequestPartyCommand(
    val requestId: UUID,
    val roleKey: InformationRequestShareRoleKey,
    val email: String,
    val displayName: String? = null,
    val access: RequestAccessContext,
    val precondition: CommandPrecondition,
    val idempotencyKey: String,
)

data class RevokeInformationRequestPartyCommand(
    val requestId: UUID,
    val partyId: UUID,
    val access: RequestAccessContext,
    val precondition: CommandPrecondition,
    val idempotencyKey: String,
)

data class ReassignInformationRequestPartyCommand(
    val requestId: UUID,
    val partyId: UUID,
    val principal: PrincipalRef,
    val exchangeRecipientId: UUID? = null,
    val access: RequestAccessContext,
    val precondition: CommandPrecondition,
    val idempotencyKey: String,
)

data class InformationRequestPartyAssignmentResult(
    val party: InformationRequestParty,
    val partiesETag: String,
    val partyETag: String,
)

data class InformationRequestPartyCopyResult(
    val copied: List<InformationRequestParty>,
    val notCopiedPartyIds: List<UUID>,
)
