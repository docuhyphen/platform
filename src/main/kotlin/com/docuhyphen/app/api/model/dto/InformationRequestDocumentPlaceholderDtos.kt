package com.docuhyphen.app.api.model.dto

import com.docuhyphen.app.api.serializer.UUIDSerializer
import kotlinx.serialization.Serializable
import java.util.*

@Serializable
data class InformationRequestDocumentPlaceholderDto(
    @Serializable(with = UUIDSerializer::class) val id: UUID,
    val title: String,
    val required: Boolean,
    val restrictType: Boolean,
    val restrictedType: String? = null,
    @Serializable(with = UUIDSerializer::class) val libraryDocumentId: UUID? = null,
    val libraryTitle: String? = null,
    val libraryDescription: String? = null,
    val libraryDocumentType: String? = null,
    val libraryFileName: String? = null,
    val libraryFileSizeBytes: Long? = null,
    val displayOrder: Int,
)
