package com.docuhyphen.app.api.model

import com.docuhyphen.app.api.model.dto.InformationRequestParticipantAccountUpgradeDto
import com.docuhyphen.app.api.model.informationrequest.InformationRequestParticipantAccountUpgrade

object InformationRequestParticipantAccountUpgradeDtoMapper
{
    fun toDto(upgrade: InformationRequestParticipantAccountUpgrade): InformationRequestParticipantAccountUpgradeDto =
        InformationRequestParticipantAccountUpgradeDto(
            participantAccountLinkId = upgrade.participantAccountLink.id,
            participantId = upgrade.participantAccountLink.participantId,
            appUserId = upgrade.participantAccountLink.appUserId,
            linkedAt = upgrade.participantAccountLink.linkedAt,
            grantedShareId = upgrade.grantedShare.id,
            grantedRoleName = upgrade.grantedShare.roleName,
        )
}
