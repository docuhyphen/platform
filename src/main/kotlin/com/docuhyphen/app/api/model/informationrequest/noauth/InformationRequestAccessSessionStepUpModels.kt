package com.docuhyphen.app.api.model.informationrequest.noauth

import com.docuhyphen.app.api.model.entity.RequestAccessSession
import java.sql.Timestamp
import java.util.*

data class InformationRequestAccessSessionStepUp(
    val sessionId: UUID,
    val verifiedUntil: Timestamp,
)

data class LockedRequestAccessSession(
    val link: LockedInformationRequestBootstrapLink,
    val session: RequestAccessSession,
)
