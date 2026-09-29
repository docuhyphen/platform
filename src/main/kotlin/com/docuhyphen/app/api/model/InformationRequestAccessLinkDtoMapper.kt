package com.docuhyphen.app.api.model

import com.docuhyphen.app.api.model.dto.InformationRequestAccessLinkDto
import com.docuhyphen.app.api.model.dto.InformationRequestAccessLinkIssuedDto
import com.docuhyphen.app.api.model.entity.ShareLink
import com.docuhyphen.app.api.service.informationrequest.InformationRequestBootstrapShareLinkIssuance
import java.util.*

object InformationRequestAccessLinkDtoMapper
{
    fun toIssuedDto(issuance: InformationRequestBootstrapShareLinkIssuance): InformationRequestAccessLinkIssuedDto =
        InformationRequestAccessLinkIssuedDto(
            shareLinkId = issuance.shareLink.id,
            accessToken = issuance.rawToken,
            status = issuance.shareLink.status,
            expiresAt = issuance.shareLink.expiresAt,
            maxUses = issuance.shareLink.maxUses,
            rotationCount = issuance.shareLink.rotationCount,
        )

    fun toDto(shareLink: ShareLink, partyId: UUID? = null): InformationRequestAccessLinkDto =
        InformationRequestAccessLinkDto(
            shareLinkId = shareLink.id,
            status = shareLink.status,
            expiresAt = shareLink.expiresAt,
            maxUses = shareLink.maxUses,
            rotationCount = shareLink.rotationCount,
            partyId = partyId,
            createdAt = shareLink.createdAt,
        )
}
