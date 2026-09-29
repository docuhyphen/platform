package com.docuhyphen.app.api.model.informationrequest

import com.docuhyphen.app.api.model.dto.InformationRequestPartyDto
import com.docuhyphen.app.api.model.entity.ShareLink
import java.util.UUID

data class InformationRequestAccessLinkView(
    val shareLink: ShareLink,
    val partyId: UUID,
)

data class InformationRequestPartyListing(
    val parties: List<InformationRequestPartyDto>,
    val partiesETag: String,
)
