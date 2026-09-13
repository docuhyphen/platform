package com.docuhyphen.app.api.model

import com.docuhyphen.app.api.model.dto.InformationRequestAccessSessionDto
import com.docuhyphen.app.api.model.informationrequest.IssuedRequestAccessSession

object InformationRequestAccessSessionDtoMapper
{
    fun toDto(issued: IssuedRequestAccessSession): InformationRequestAccessSessionDto = with(issued.session) {
        InformationRequestAccessSessionDto(
            sessionId = id,
            verificationStrength = verificationStrength,
            issuedAt = issuedAt,
            expiresAt = requireNotNull(expiresAt),
            sessionToken = issued.sessionToken,
        )
    }
}
