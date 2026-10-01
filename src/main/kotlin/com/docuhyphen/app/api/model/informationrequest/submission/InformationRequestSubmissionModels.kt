package com.docuhyphen.app.api.model.informationrequest.submission

import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.attestation.InformationRequestAttestationRequirementEvaluation
import com.docuhyphen.app.api.model.informationrequest.evidence.InformationRequestEvidenceConformance
import com.docuhyphen.app.api.model.informationrequest.evidence.InformationRequestEvidenceRequirementState
import com.docuhyphen.app.api.model.informationrequest.response.InformationRequestCompletenessItemState
import java.util.*

data class InformationRequestEvidenceSubmissionMember(
    val artifactId: UUID,
    val versionId: UUID,
    val versionNumber: Int,
    val documentVersionId: UUID?,
    val contentHashAlgorithm: String?,
    val contentHash: String?,
    val contentLength: Long?,
    val contentVerification: String?,
    val conformance: InformationRequestEvidenceConformance,
    val inspectionAssessmentId: UUID?,
    val malwareAssessmentId: UUID?,
)

data class InformationRequestEvidenceSubmissionFacts(
    val state: InformationRequestEvidenceRequirementState,
    val members: List<InformationRequestEvidenceSubmissionMember>,
)

data class InformationRequestSubmissionContentItem(
    val requirement: InformationRequestRequirement,
    val revision: InformationRequestRequirementRevision,
    val binding: InformationRequestTemplateRequirementBinding,
    val requirementKey: String,
    val requirementType: InformationRequestRequirementType,
    val response: InformationRequestResponse?,
    val fieldValueRevisionId: UUID?,
    val evidence: InformationRequestEvidenceSubmissionFacts?,
)

data class InformationRequestSubmissionContent(
    val request: InformationRequest,
    val version: InformationRequestTemplateVersion,
    val stageKey: String?,
    val stageOrder: List<String>,
    val items: List<InformationRequestSubmissionContentItem>,
    val links: List<InformationRequestSupportingEvidenceLink>,
    val contentHash: String,
)

data class InformationRequestSubmissionAssessment(
    val readiness: InformationRequestSubmissionReadiness,
    val stateByRequirement: Map<UUID, InformationRequestCompletenessItemState>,
    val attestations: Map<UUID, InformationRequestAttestationRequirementEvaluation>,
)

internal data class InformationRequestSubmittedScope(
    val items: List<InformationRequestSubmissionItem>,
    val correctionByPackage: Map<UUID, InformationRequestCorrection>,
    val allowlistedByCorrection: Map<UUID, Set<UUID>>,
)
{
    fun editable(item: InformationRequestSubmissionItem): Boolean
    {
        val correction = correctionByPackage[item.packageId] ?: return false
        return item.informationRequestRequirementId in allowlistedByCorrection[correction.id].orEmpty() ||
                item.completenessState == InformationRequestCompletenessItemState.HIDDEN
    }
}
