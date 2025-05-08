package com.dochyphen.app.api.model.dto

import com.dochyphen.app.api.serializer.UUIDSerializer
import kotlinx.serialization.Serializable
import java.util.*

@Serializable
data class OrganizationSettingsDto(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID? = null,
    val allowShareWithoutPairing: Boolean = false,
    val allowProfileUpdate: Boolean = false,
    val allowEmailUpdate: Boolean = false
)