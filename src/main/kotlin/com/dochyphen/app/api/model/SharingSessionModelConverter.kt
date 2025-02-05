package com.dochyphen.app.api.model

import com.dochyphen.app.api.model.dto.SharingSessionBasicDto

class SharingSessionModelConverter
{
    companion object
    {
        fun convertToBasicDto(sharingSession: SharingSession): SharingSessionBasicDto
        {
            return SharingSessionBasicDto(
                sharingSession.id,
                sharingSession.createdDate,
                sharingSession.lastActivity,
                sharingSession.sessionName,
                sharingSession.initialShareMessage,
                sharingSession.description,
                sharingSession.initiator?.id.toString(),
                sharingSession.receiver?.id.toString(),
                sharingSession.status.toString(),
//                sharingSession.participants.map { it.id }
            )
        }
    }


}