package com.docuhyphen.app.api.model.informationrequest.privacy

import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
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
    val idempotencyKey: String,
    val authorization: AuthorizationContext,
    val transferRegion: String? = null,
    val correction: InformationRequestItemCorrectionInput? = null,
)

data class LiftInformationRequestSubjectRestrictionCommand(
    val restrictionId: UUID,
    val reasonCode: String,
    val idempotencyKey: String,
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
    val recordExportAvailable: Boolean = false,
)

data class InformationRequestPrivacyOutcome(
    val view: InformationRequestPrivacyRequestView,
    val claimIds: List<UUID>,
)

data class InformationRequestPrivacyRefusal(
    val reasonCode: String,
    val detail: String,
    val targets: Map<UUID, String>,
)
