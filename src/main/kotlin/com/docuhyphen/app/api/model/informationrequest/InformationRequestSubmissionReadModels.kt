package com.docuhyphen.app.api.model.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequestSubmissionAttestation
import com.docuhyphen.app.api.model.entity.InformationRequestSubmissionMode
import com.docuhyphen.app.api.model.entity.InformationRequestSubmissionPackage
import com.docuhyphen.app.api.model.entity.InformationRequestSubmissionStageOrdering
import com.docuhyphen.app.api.model.fields.FieldValueRevisionValue
import java.util.UUID

data class InformationRequestReadableSubmissionPackage(
    val view: InformationRequestSubmissionPackageView,
    val visibleRequirementIds: Set<UUID>,
    val fieldValues: Map<UUID, FieldValueRevisionValue>,
)

data class InformationRequestAttestationStanding(
    val requirementId: UUID,
    val requirementKey: String,
    val prompt: String,
    val evaluated: InformationRequestAttestationRequirementEvaluation,
    val callerCanAttest: Boolean,
    val attestations: List<InformationRequestSubmissionAttestation>,
)

data class InformationRequestSubmissionStageStanding(
    val stageKey: String,
    val submittedPackage: InformationRequestSubmissionPackage?,
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
    val packages: List<InformationRequestReadableSubmissionPackage>,
)
