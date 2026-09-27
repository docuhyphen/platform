package com.docuhyphen.app.api.model.communication

import com.docuhyphen.app.api.model.entity.CommunicationScope
import java.util.UUID

data class CommunicationSource(
    val id: UUID,
    val scope: CommunicationScope,
    val organizationId: UUID?,
    val createdByAppUserId: UUID?,
    val subject: String,
    val body: String,
)
