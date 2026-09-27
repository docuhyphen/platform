package com.docuhyphen.app.api.model.dto

import com.docuhyphen.app.api.model.entity.InformationRequestPrivacyRequestKind
import com.docuhyphen.app.api.model.entity.InformationRequestPrivacyRequestState
import com.docuhyphen.app.api.model.entity.InformationRequestPrivacyTargetOutcome
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.serializer.TimestampSerializer
import com.docuhyphen.app.api.serializer.UUIDSerializer
import kotlinx.serialization.Serializable
import java.sql.Timestamp
import java.util.UUID

@Serializable
data class InformationRequestPrivacyTargetDto(
    @Serializable(with = UUIDSerializer::class) val requestId: UUID,
    val outcome: InformationRequestPrivacyTargetOutcome,
    val reasonCode: String? = null,
    @Serializable(with = UUIDSerializer::class) val disposalClaimId: UUID? = null,
)

@Serializable
data class InformationRequestSubjectRestrictionDto(
    @Serializable(with = UUIDSerializer::class) val id: UUID,
    @Serializable(with = UUIDSerializer::class) val subjectIdentityRefId: UUID,
    @Serializable(with = UUIDSerializer::class) val privacyRequestId: UUID,
    @Serializable(with = TimestampSerializer::class) val restrictedAt: Timestamp,
    @Serializable(with = TimestampSerializer::class) val liftedAt: Timestamp? = null,
    val liftReasonCode: String? = null,
)

@Serializable
data class InformationRequestItemCorrectionDto(
    @Serializable(with = UUIDSerializer::class) val id: UUID,
    @Serializable(with = UUIDSerializer::class) val requestId: UUID,
    @Serializable(with = UUIDSerializer::class) val packageId: UUID,
    @Serializable(with = UUIDSerializer::class) val submissionItemId: UUID,
    val reasonCode: String,
    @Serializable(with = TimestampSerializer::class) val recordedAt: Timestamp,
)

@Serializable
data class InformationRequestPrivacyRequestDto(
    @Serializable(with = UUIDSerializer::class) val id: UUID,
    @Serializable(with = UUIDSerializer::class) val subjectIdentityRefId: UUID,
    val requestKind: InformationRequestPrivacyRequestKind,
    val purposeKey: String,
    val policyBasisKey: String,
    val state: InformationRequestPrivacyRequestState,
    val refusalCode: String? = null,
    val refusalDetail: String? = null,
    @Serializable(with = UUIDSerializer::class) val recordExportId: UUID? = null,
    val recordedByPrincipalKind: PrincipalKind,
    @Serializable(with = UUIDSerializer::class) val recordedByPrincipalId: UUID,
    @Serializable(with = TimestampSerializer::class) val recordedAt: Timestamp,
    @Serializable(with = TimestampSerializer::class) val completedAt: Timestamp? = null,
    val targets: List<InformationRequestPrivacyTargetDto> = emptyList(),
    val restriction: InformationRequestSubjectRestrictionDto? = null,
    val correction: InformationRequestItemCorrectionDto? = null,
)
