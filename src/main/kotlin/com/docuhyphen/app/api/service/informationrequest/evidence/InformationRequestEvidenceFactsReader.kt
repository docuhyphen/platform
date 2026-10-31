package com.docuhyphen.app.api.service.informationrequest.evidence

import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.evidence.*
import com.docuhyphen.app.api.repository.informationrequest.evidence.InformationRequestEvidenceArtifactRepository
import com.docuhyphen.app.api.repository.informationrequest.evidence.InformationRequestEvidenceAssessmentRepository
import com.docuhyphen.app.api.repository.informationrequest.evidence.InformationRequestEvidenceVersionRepository
import com.docuhyphen.app.api.service.document.DocumentVersionRecordingService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject

@ApplicationScoped
class InformationRequestEvidenceFactsReader @Inject constructor(
    private val artifactRepository: InformationRequestEvidenceArtifactRepository,
    private val versionRepository: InformationRequestEvidenceVersionRepository,
    private val assessmentRepository: InformationRequestEvidenceAssessmentRepository,
    private val documentVersionRecordingService: DocumentVersionRecordingService,
)
{
    fun facts(requirement: InformationRequestRequirement): List<InformationRequestEvidenceVersionFacts>
    {
        val versionsByArtifact = artifactRepository.findForRequirement(requirement.id)
            .associateWith { versionRepository.findForArtifact(it.id) }
        val assessments = assessmentRepository.findForVersions(versionsByArtifact.values.flatten().map { it.id })
            .groupBy { it.evidenceVersionId }

        return versionsByArtifact.flatMap { (artifact, versions) ->
            val latestNumber = versions.maxOfOrNull { it.versionNumber }
            versions.map { version -> factsOf(artifact, version, latestNumber, assessments[version.id].orEmpty()) }
        }
    }

    private fun factsOf(
        artifact: InformationRequestEvidenceArtifact,
        version: InformationRequestEvidenceVersion,
        latestNumber: Int?,
        assessments: List<InformationRequestEvidenceAssessment>,
    ): InformationRequestEvidenceVersionFacts
    {
        val content =
            documentVersionRecordingService.findVersion(InformationRequestEvidenceVersionSourceMapper.read(version).documentVersionId)

        return InformationRequestEvidenceVersionFacts(
            versionId = version.id,
            artifactId = artifact.id,
            versionNumber = version.versionNumber,
            standing = when
            {
                artifact.collectionState == InformationRequestEvidenceCollectionState.REMOVED -> InformationRequestEvidenceStanding.REMOVED
                artifact.collectionState == InformationRequestEvidenceCollectionState.WITHDRAWN -> InformationRequestEvidenceStanding.WITHDRAWN
                version.versionNumber == latestNumber -> InformationRequestEvidenceStanding.CURRENT
                else -> InformationRequestEvidenceStanding.SUPERSEDED
            },
            contentVerification = content?.contentVerification,
            contentLength = content?.contentLength,
            declaredMediaType = version.declaredMediaType,
            declaredFileName = version.declaredFileName,
            attributes = InformationRequestEvidenceAttributesMapper.read(version),
            inspection = InformationRequestEvidenceAssessmentSelection.latestInspection(assessments)?.inspectionFacts(),
            malware = InformationRequestEvidenceAssessmentSelection.governingScan(assessments)?.malwareFacts(),
        )
    }
}
