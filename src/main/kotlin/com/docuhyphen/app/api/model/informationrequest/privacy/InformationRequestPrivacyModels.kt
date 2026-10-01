package com.docuhyphen.app.api.model.informationrequest.privacy

import com.docuhyphen.app.api.model.entity.*
import kotlinx.serialization.json.JsonElement
import java.util.*

data class InformationRequestItemCorrectionInput(
    val submissionItemId: UUID,
    val value: JsonElement?,
    val narrative: String?,
    val reasonCode: String,
)

data class RecordInformationRequestPrivacyRequestCommand(
    val subjectIdentityRefId: UUID,
    val requestKind: InformationRequestPrivacyRequestKind,
    val purposeKey: String,
    val policyBasisKey: String,
    val transferRegion: String? = null,
    val correction: InformationRequestItemCorrectionInput? = null,
)

data class InformationRequestPrivacyTarget(
    val requestId: UUID,
    val outcome: InformationRequestPrivacyTargetOutcome,
    val reasonCode: String?,
    val disposalClaimId: UUID?,
)

data class InformationRequestPrivacyRequestView(
    val request: InformationRequestPrivacyRequest,
    val targets: List<InformationRequestPrivacyTarget>,
    val restriction: InformationRequestSubjectRestriction?,
    val correction: InformationRequestItemCorrection?,
)
