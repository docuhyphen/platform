package com.docuhyphen.app.api.model.dto

import com.docuhyphen.app.api.model.entity.FieldValueType
import com.docuhyphen.app.api.model.entity.InformationRequestAcceptedFactConfidence
import com.docuhyphen.app.api.model.entity.InformationRequestAcceptedFactConflictState
import com.docuhyphen.app.api.model.entity.InformationRequestAcceptedFactVisibility
import com.docuhyphen.app.api.model.entity.InformationRequestBusinessDecisionKind
import com.docuhyphen.app.api.model.informationrequest.acceptedfact.InformationRequestAcceptedFactFreshness
import com.docuhyphen.app.api.serializer.TimestampSerializer
import com.docuhyphen.app.api.serializer.UUIDSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import java.sql.Timestamp
import java.util.UUID

@Serializable
data class InformationRequestAcceptedFactDto(
    @Serializable(with = UUIDSerializer::class) val id: UUID,
    @Serializable(with = UUIDSerializer::class) val subjectIdentityRefId: UUID,
    val purposeKey: String,
    val policyBasisKey: String,
    val evidenceVersionIds: List<@Serializable(with = UUIDSerializer::class) UUID>,
    @Serializable(with = UUIDSerializer::class) val fieldDefinitionId: UUID,
    val valueType: FieldValueType,
    val value: JsonElement,
    @Serializable(with = UUIDSerializer::class) val sourceInformationRequestId: UUID,
    @Serializable(with = UUIDSerializer::class) val sourcePackageId: UUID,
    @Serializable(with = UUIDSerializer::class) val sourceSubmissionItemId: UUID,
    @Serializable(with = UUIDSerializer::class) val sourceRequirementId: UUID,
    @Serializable(with = UUIDSerializer::class) val sourceReviewId: UUID? = null,
    val visibility: InformationRequestAcceptedFactVisibility,
    val confidence: InformationRequestAcceptedFactConfidence,
    @Serializable(with = TimestampSerializer::class) val validFrom: Timestamp,
    @Serializable(with = TimestampSerializer::class) val validTo: Timestamp? = null,
    @Serializable(with = TimestampSerializer::class) val expiresAt: Timestamp? = null,
    @Serializable(with = UUIDSerializer::class) val supersedesFactId: UUID? = null,
    @Serializable(with = UUIDSerializer::class) val supersededByFactId: UUID? = null,
    val conflictState: InformationRequestAcceptedFactConflictState,
    @Serializable(with = UUIDSerializer::class) val conflictingFactId: UUID? = null,
    @Serializable(with = TimestampSerializer::class) val promotedAt: Timestamp,
    val revoked: Boolean,
    @Serializable(with = TimestampSerializer::class) val revokedAt: Timestamp? = null,
    val revocationReasonCode: String? = null,
    val freshness: InformationRequestAcceptedFactFreshness,
)

@Serializable
data class InformationRequestAcceptedFactOfferDto(
    @Serializable(with = UUIDSerializer::class) val requirementId: UUID,
    val requirementKey: String,
    val fact: InformationRequestReusableFactDto,
    val reconfirmationRequired: Boolean,
)

@Serializable
data class InformationRequestReusableFactDto(
    @Serializable(with = UUIDSerializer::class) val id: UUID,
    val purposeKey: String,
    val policyBasisKey: String,
    val valueType: FieldValueType,
    val value: JsonElement,
    val confidence: InformationRequestAcceptedFactConfidence,
    @Serializable(with = TimestampSerializer::class) val validFrom: Timestamp,
    @Serializable(with = TimestampSerializer::class) val validTo: Timestamp? = null,
    @Serializable(with = TimestampSerializer::class) val expiresAt: Timestamp? = null,
    val freshness: InformationRequestAcceptedFactFreshness,
)

@Serializable
data class InformationRequestFactRecertificationDto(
    @Serializable(with = UUIDSerializer::class) val id: UUID,
    @Serializable(with = UUIDSerializer::class) val requirementId: UUID,
    @Serializable(with = UUIDSerializer::class) val factId: UUID,
    @Serializable(with = UUIDSerializer::class) val responseId: UUID,
    val responseRevision: Long,
    val valueType: FieldValueType,
    val value: JsonElement,
    @Serializable(with = TimestampSerializer::class) val assentedAt: Timestamp,
)

@Serializable
data class InformationRequestBusinessDecisionDto(
    @Serializable(with = UUIDSerializer::class) val id: UUID,
    @Serializable(with = UUIDSerializer::class) val informationRequestId: UUID,
    val owningProcessKey: String,
    val outcomeCode: String,
    val reasonReference: String? = null,
    val externalReference: String? = null,
    val kind: InformationRequestBusinessDecisionKind,
    @Serializable(with = UUIDSerializer::class) val priorDecisionId: UUID? = null,
    val decisionRevision: Int,
    @Serializable(with = TimestampSerializer::class) val decidedAt: Timestamp,
    @Serializable(with = TimestampSerializer::class) val recordedAt: Timestamp,
    val recordedByCaller: Boolean,
)
