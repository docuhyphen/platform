package com.docuhyphen.app.api.model.informationrequest.submission

import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.fields.FieldValueRevisionValue
import com.docuhyphen.app.api.model.informationrequest.attestation.InformationRequestActingParty
import com.docuhyphen.app.api.model.informationrequest.attestation.InformationRequestAttestationRequirementEvaluation
import com.docuhyphen.app.api.model.informationrequest.evidence.InformationRequestEvidenceVersionRecord
import java.util.*

data class InformationRequestReadableSubmissionPackage(
    val view: InformationRequestSubmissionPackageView,
    val visibleRequirementIds: Set<UUID>,
    val fieldValues: Map<UUID, FieldValueRevisionValue>,
    val evidenceVersions: Map<UUID, InformationRequestEvidenceVersionRecord>,
    val current: Boolean = true,
    val withdrawable: Boolean = false,
)

data class InformationRequestAttestationStanding(
    val requirementId: UUID,
    val requirementKey: String,
    val prompt: String,
    val evaluated: InformationRequestAttestationRequirementEvaluation,
    val callerCanAttest: Boolean,
    val waitingForRoles: List<InformationRequestContributorRole>,
    val attestations: List<InformationRequestSubmissionAttestation>,
    val attestingParties: List<InformationRequestActingParty> = emptyList(),
)

data class InformationRequestSubmissionStageStanding(
    val stageKey: String,
    val submittedPackage: InformationRequestSubmissionPackage?,
    val correctionOpen: Boolean,
)

data class InformationRequestSubmissionPreview(
    val requestId: UUID,
    val stageKey: String?,
    val submissionMode: InformationRequestSubmissionMode,
    val submissionStageOrdering: InformationRequestSubmissionStageOrdering,
    val submissionETag: String,
    val readiness: InformationRequestSubmissionReadiness,
    val attestations: List<InformationRequestAttestationStanding>,
    val stages: List<InformationRequestSubmissionStageStanding>,
    val canSubmit: Boolean,
    val correctionOpen: Boolean,
    val packages: List<InformationRequestReadableSubmissionPackage>,
    val scopeItems: List<InformationRequestSubmissionScopeItem> = emptyList(),
    val undisclosedScopeItemCount: Int = 0,
)
