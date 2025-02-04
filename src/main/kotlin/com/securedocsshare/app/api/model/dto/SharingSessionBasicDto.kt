package com.securedocsshare.app.api.model.dto

import com.securedocsshare.app.api.serializer.TimestampSerializer
import com.securedocsshare.app.api.serializer.UUIDSerializer
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.sql.Timestamp
import java.util.*

@Serializable
data class SharingSessionBasicDto(

    @Serializable(with = UUIDSerializer::class)
    var id: UUID,

    @Serializable(with = TimestampSerializer::class)
    var createdDate: Timestamp,

    @Serializable(with = TimestampSerializer::class)
    var lastActivity: Timestamp,

    var sessionName: String?,
    var initialShareMessage: String? = null,
    var description: String? = null,
    var initiatorId: String,
    var receiverId: String,
    var status: String,

//    var participantIds: List<UUID>
)