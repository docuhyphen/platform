package com.docuhyphen.app.api.model.exchange

import java.util.*

sealed class ExternalParticipantOwner {
    data class Organization(val organizationId: UUID) : ExternalParticipantOwner()
    data class Personal(val userId: UUID) : ExternalParticipantOwner()
}
