package com.docuhyphen.app.api.resource.model

import com.docuhyphen.app.api.model.entity.InformationRequestPrivacyRequestKind
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class CreateInformationRequestRecordExportRequest(
    val transferRegion: String? = null,
)

@Serializable
data class InformationRequestItemCorrectionRequest(
    val submissionItemId: String,
    val value: JsonElement? = null,
    val narrative: String? = null,
    val reasonCode: String,
)

@Serializable
data class RecordInformationRequestPrivacyRequestRequest(
    val subjectIdentityRefId: String,
    val requestKind: InformationRequestPrivacyRequestKind,
    val purposeKey: String,
    val policyBasisKey: String,
    val transferRegion: String? = null,
    val correction: InformationRequestItemCorrectionRequest? = null,
)

@Serializable
data class LiftInformationRequestSubjectRestrictionRequest(
    val reasonCode: String,
)
