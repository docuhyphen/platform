package com.docuhyphen.app.api.model

import com.docuhyphen.app.api.model.dto.InformationRequestPartyDto
import com.docuhyphen.app.api.model.entity.InformationRequestParty
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag

object InformationRequestPartyDtoMapper
{
    fun toDto(party: InformationRequestParty, revealIdentity: Boolean): InformationRequestPartyDto =
        InformationRequestPartyDto(
            id = party.id,
            informationRequestId = party.informationRequestId,
            roleKey = party.roleKey,
            active = party.active,
            principalId = party.principalId.takeIf { revealIdentity },
            principalKind = party.principalKind.takeIf { revealIdentity },
            subjectIdentityRefId = party.subjectIdentityRefId.takeIf { revealIdentity },
            exchangeRecipientId = party.exchangeRecipientId.takeIf { revealIdentity },
            assignedAt = party.assignedAt,
            revokedAt = party.revokedAt,
            partyRevision = party.partyRevision,
            partyETag = InformationRequestETag.partyOf(party),
        )
}
