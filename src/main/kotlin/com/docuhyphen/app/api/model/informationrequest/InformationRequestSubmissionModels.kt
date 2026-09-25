package com.docuhyphen.app.api.model.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestRequirement
import com.docuhyphen.app.api.model.entity.InformationRequestRequirementRevision
import com.docuhyphen.app.api.model.entity.InformationRequestRequirementType
import com.docuhyphen.app.api.model.entity.InformationRequestResponse
import com.docuhyphen.app.api.model.entity.InformationRequestSupportingEvidenceLink
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateRequirementBinding
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateVersion
import java.util.UUID

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
