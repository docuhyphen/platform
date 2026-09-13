package com.docuhyphen.app.api.model.informationrequest

import com.docuhyphen.app.api.model.entity.ParticipantAccountLink
import com.docuhyphen.app.api.model.entity.Share
import com.docuhyphen.app.api.service.command.CommandPrecondition
import java.util.UUID

data class UpgradeInformationRequestParticipantAccountCommand(
    val requestId: UUID,
    val sessionId: UUID,
    val appUserId: UUID,
    val precondition: CommandPrecondition,
    val idempotencyKey: String,
    val sessionToken: String? = null,
)
{
    override fun toString(): String = "UpgradeInformationRequestParticipantAccountCommand(requestId=$requestId, sessionId=$sessionId)"
}

data class InformationRequestParticipantAccountUpgrade(
    val participantAccountLink: ParticipantAccountLink,
    val grantedShare: Share,
)
