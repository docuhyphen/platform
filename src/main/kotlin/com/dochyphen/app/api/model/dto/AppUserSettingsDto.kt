package com.dochyphen.app.api.model.dto

import com.dochyphen.app.api.serializer.UUIDSerializer
import kotlinx.serialization.Serializable
import java.util.*

@Serializable
data class AppUserSettingsDto(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID? = null,
    val notifyLogin: Boolean = true,
    val autoPreviewDocuments: Boolean = true,
    val notifyShareStart: Boolean = true,
    val notifyShareAccept: Boolean = true,
    val notifyShareDecline: Boolean = true,
    val notifyShareEnd: Boolean = true,
    val notifyDocComment: Boolean = true,
    val notifyDocDelete: Boolean = true,
    val notifyDocAdd: Boolean = true,
    val notifyDocUpload: Boolean = true
)