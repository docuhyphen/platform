package com.docuhyphen.app.api.model.informationrequest.noauth

import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.entity.ParticipantAccountLink
import com.docuhyphen.app.api.model.entity.Share
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import java.util.*

data class UpgradeInformationRequestParticipantAccountCommand(
    val requestId: UUID,
    val sessionId: UUID,
    val sessionCredential: String?,
    val access: RequestAccessContext,
    val precondition: CommandPrecondition,
    val idempotencyKey: String,
)
{
    override fun toString(): String =
        "UpgradeInformationRequestParticipantAccountCommand(requestId=$requestId, sessionId=$sessionId)"
}

data class InformationRequestParticipantAccountUpgrade(
    val participantAccountLink: ParticipantAccountLink,
    val grantedShare: Share,
    val partyETag: String,
    val accountRespondentKey: String,
)
