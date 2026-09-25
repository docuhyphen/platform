package com.docuhyphen.app.api.model

import com.docuhyphen.app.api.model.dto.InformationRequestEvidenceArtifactDto
import com.docuhyphen.app.api.model.dto.InformationRequestEvidenceCommandResultDto
import com.docuhyphen.app.api.model.dto.InformationRequestEvidenceEvaluationDto
import com.docuhyphen.app.api.model.dto.InformationRequestEvidenceFindingDto
import com.docuhyphen.app.api.model.dto.InformationRequestEvidenceListDto
import com.docuhyphen.app.api.model.dto.InformationRequestEvidenceVersionDto
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceArtifactView
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceCommandResult
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceFinding
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceList
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceRequirementEvaluation
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceVersionEvaluation
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceVersionView
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import java.util.UUID

object InformationRequestEvidenceDtoMapper
{
    fun toDto(list: InformationRequestEvidenceList, caller: PrincipalRef): InformationRequestEvidenceListDto
    {
        val versionEvaluations = list.evaluation?.versions.orEmpty().associateBy { it.versionId }
        return InformationRequestEvidenceListDto(
            requirementId = list.requirementId,
            evidenceETag = list.evidenceETag,
            artifacts = list.artifacts.map { toDto(it, caller, versionEvaluations) },
            evaluation = list.evaluation?.let(::toDto),
        )
    }

    fun toDto(result: InformationRequestEvidenceCommandResult, caller: PrincipalRef): InformationRequestEvidenceCommandResultDto =
        InformationRequestEvidenceCommandResultDto(
            artifact = toDto(result.artifact, caller).copy(etag = result.artifactETag),
            evidenceETag = result.evidenceETag,
            artifactETag = result.artifactETag,
        )

    fun toDto(
        view: InformationRequestEvidenceArtifactView,
        caller: PrincipalRef,
        versionEvaluations: Map<UUID, InformationRequestEvidenceVersionEvaluation> = emptyMap(),
    ): InformationRequestEvidenceArtifactDto
    {
        val artifact = view.artifact
        return InformationRequestEvidenceArtifactDto(
            id = artifact.id,
            requirementId = artifact.informationRequestRequirementId,
            artifactKey = artifact.artifactKey,
            collectionState = artifact.collectionState,
            artifactRevision = artifact.artifactRevision,
            etag = InformationRequestETag.artifactOf(artifact),
            createdByCaller = artifact.createdByPrincipalKind == caller.kind && artifact.createdByPrincipalId == caller.id,
            stateReason = artifact.stateReason,
            stateChangedAt = artifact.stateChangedAt,
            createdAt = artifact.createdAt,
            updatedAt = artifact.updatedAt,
            versions = view.versions.map { toDto(it, caller, versionEvaluations[it.version.id]) },
        )
    }

    private fun toDto(evaluation: InformationRequestEvidenceRequirementEvaluation): InformationRequestEvidenceEvaluationDto =
        InformationRequestEvidenceEvaluationDto(
            state = evaluation.state,
            completesWork = evaluation.state.completesWork,
            satisfiedBySubstitute = evaluation.satisfiedBySubstitute,
            findings = evaluation.findings.map(::toDto),
        )

    private fun toDto(finding: InformationRequestEvidenceFinding): InformationRequestEvidenceFindingDto =
        InformationRequestEvidenceFindingDto(finding.code, finding.code.blocking, finding.detail)

    private fun toDto(
        view: InformationRequestEvidenceVersionView,
        caller: PrincipalRef,
        evaluation: InformationRequestEvidenceVersionEvaluation?,
    ): InformationRequestEvidenceVersionDto
    {
        val version = view.version
        val content = view.documentVersion
        return InformationRequestEvidenceVersionDto(
            id = version.id,
            versionNumber = version.versionNumber,
            sourceKind = version.sourceKind,
            declaredFileName = version.declaredFileName,
            declaredMediaType = version.declaredMediaType,
            contentLength = content?.contentLength,
            contentHashAlgorithm = content?.contentHashAlgorithm,
            contentHash = content?.contentHash,
            contentVerification = content?.contentVerification,
            externalReferenceType = version.externalReferenceType,
            externalReferenceValue = version.externalReferenceValue,
            issuer = version.issuer,
            jurisdiction = version.jurisdiction,
            language = version.language,
            issuedOn = version.issuedOn?.toString(),
            expiresOn = version.expiresOn?.toString(),
            coverageStartsOn = version.coverageStartsOn?.toString(),
            coverageEndsOn = version.coverageEndsOn?.toString(),
            certificationReference = version.certificationReference,
            signatureReference = version.signatureReference,
            createdByCaller = version.createdByPrincipalKind == caller.kind && version.createdByPrincipalId == caller.id,
            createdAt = version.createdAt,
            conformance = evaluation?.conformance,
            findings = evaluation?.findings.orEmpty().map(::toDto),
        )
    }
}
