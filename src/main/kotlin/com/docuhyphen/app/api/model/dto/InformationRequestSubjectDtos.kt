package com.docuhyphen.app.api.model.dto

import com.docuhyphen.app.api.model.entity.SubjectKind
import com.docuhyphen.app.api.serializer.TimestampSerializer
import com.docuhyphen.app.api.serializer.UUIDSerializer
import kotlinx.serialization.Serializable
import java.sql.Timestamp
import java.util.*

@Serializable
data class InformationRequestSubjectReferenceDto(
    val authority: String,
    val identifierType: String,
    val identifierValue: String,
)

@Serializable
data class InformationRequestSubjectDto(
    @Serializable(with = UUIDSerializer::class) val id: UUID,
    val subjectKind: SubjectKind,
    val references: List<InformationRequestSubjectReferenceDto>,
    @Serializable(with = TimestampSerializer::class) val createdAt: Timestamp,
)
