package com.docuhyphen.app.api.service.informationrequest.party

import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestOwnerType
import com.docuhyphen.app.api.model.exchange.ExternalParticipantOwner

object InformationRequestParticipantOwner {
    fun of(request: InformationRequest): ExternalParticipantOwner = when (request.ownerType) {
        InformationRequestOwnerType.ORGANIZATION ->
            ExternalParticipantOwner.Organization(requireNotNull(request.ownerOrganizationId))

        InformationRequestOwnerType.USER ->
            ExternalParticipantOwner.Personal(requireNotNull(request.ownerUserId))
    }
}
