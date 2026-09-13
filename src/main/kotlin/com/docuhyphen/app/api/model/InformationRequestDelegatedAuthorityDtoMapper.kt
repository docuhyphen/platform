package com.docuhyphen.app.api.model

import com.docuhyphen.app.api.model.dto.InformationRequestDelegatedAuthorityDto
import com.docuhyphen.app.api.model.entity.InformationRequestDelegatedAuthority

object InformationRequestDelegatedAuthorityDtoMapper
{
    fun toDto(authority: InformationRequestDelegatedAuthority): InformationRequestDelegatedAuthorityDto =
        InformationRequestDelegatedAuthorityDto(
            id = authority.id,
            informationRequestId = authority.informationRequestId,
            assignedPartyId = authority.assignedPartyId,
            delegatePrincipalKind = authority.delegatePrincipalKind,
            delegatePrincipalId = authority.delegatePrincipalId,
            requirementId = authority.requirementId,
            active = authority.active,
            grantorPrincipalKind = authority.grantorPrincipalKind,
            grantorPrincipalId = authority.grantorPrincipalId,
            authorityInstrumentRef = authority.authorityInstrumentRef,
            effectiveAt = authority.effectiveAt,
            expiresAt = authority.expiresAt,
            revokedAt = authority.revokedAt,
            revokedByPrincipalKind = authority.revokedByPrincipalKind,
            revokedByPrincipalId = authority.revokedByPrincipalId,
            revocationReason = authority.revocationReason,
            recordedAt = authority.recordedAt,
            updatedAt = authority.updatedAt,
        )
}
