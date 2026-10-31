package com.docuhyphen.app.api.model

import com.docuhyphen.app.api.model.dto.InformationRequestAccessSessionDto
import com.docuhyphen.app.api.model.dto.InformationRequestAccessSessionStepUpDto
import com.docuhyphen.app.api.model.informationrequest.noauth.InformationRequestAccessSessionStepUp
import com.docuhyphen.app.api.model.informationrequest.noauth.IssuedRequestAccessSession

object InformationRequestAccessSessionDtoMapper
{
    fun toDto(issued: IssuedRequestAccessSession): InformationRequestAccessSessionDto = with(issued.session) {
        InformationRequestAccessSessionDto(
            sessionId = id,
            verificationStrength = verificationStrength,
            issuedAt = issuedAt,
            expiresAt = expiresAt,
            sessionToken = issued.sessionToken,
        )
    }

    fun toDto(stepUp: InformationRequestAccessSessionStepUp): InformationRequestAccessSessionStepUpDto =
        InformationRequestAccessSessionStepUpDto(sessionId = stepUp.sessionId, verifiedUntil = stepUp.verifiedUntil)
}
