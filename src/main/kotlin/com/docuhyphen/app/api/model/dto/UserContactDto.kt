package com.docuhyphen.app.api.model.dto

import com.docuhyphen.app.api.serializer.TimestampSerializer
import com.docuhyphen.app.api.serializer.UUIDSerializer
import kotlinx.serialization.Serializable
import java.sql.Timestamp
import java.util.*

@Serializable
data class UserContactDto(
    @Serializable(with = UUIDSerializer::class)
    val contactAppUserId: UUID?,
    val email: String,
    val firstName: String?,
    val lastName: String?,
    @Serializable(with = TimestampSerializer::class)
    val lastSharedAt: Timestamp,
    val shareCount: Int,
)
