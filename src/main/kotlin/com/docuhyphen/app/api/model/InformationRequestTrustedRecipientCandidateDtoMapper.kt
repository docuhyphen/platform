package com.docuhyphen.app.api.model

import com.docuhyphen.app.api.model.dto.InformationRequestTrustedRecipientCandidateDto
import com.docuhyphen.app.api.model.informationrequest.party.InformationRequestTrustedRecipientCandidate

object InformationRequestTrustedRecipientCandidateDtoMapper {
    fun toDto(candidate: InformationRequestTrustedRecipientCandidate): InformationRequestTrustedRecipientCandidateDto =
        InformationRequestTrustedRecipientCandidateDto(
            exchangeRecipientId = candidate.exchangeRecipientId,
            selectionType = candidate.selectionType,
            principalKind = candidate.principal.kind,
            principalId = candidate.principal.id,
            label = candidate.label,
            targetOrganizationId = candidate.targetOrganizationId,
            targetOrganizationName = candidate.targetOrganizationName,
            trustSuspended = candidate.trustSuspended,
        )
}
