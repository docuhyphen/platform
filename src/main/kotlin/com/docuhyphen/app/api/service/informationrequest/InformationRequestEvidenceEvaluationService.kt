package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceArtifact
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceAssessment
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceCollectionState
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceVersion
import com.docuhyphen.app.api.model.entity.InformationRequestRequirement
import com.docuhyphen.app.api.model.entity.InformationRequestResponseDisposition
import com.docuhyphen.app.api.model.informationrequest.InformationRequestDocumentVersionEvidenceSource
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceAssessmentSelection
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceAttributesMapper
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceConformance
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceSubmissionFacts
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceSubmissionMember
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidencePolicyEvaluator
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceRequirementEvaluation
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceRequirementState
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceStanding
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceVersionFacts
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceVersionSourceMapper
import com.docuhyphen.app.api.model.informationrequest.inspectionFacts
import com.docuhyphen.app.api.model.informationrequest.malwareFacts
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestEvidenceArtifactRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestEvidenceAssessmentRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestEvidenceVersionRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateBindingSubstituteRepository
import com.docuhyphen.app.api.service.document.DocumentVersionRecordingService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.time.Clock
import java.time.LocalDate

@ApplicationScoped
class InformationRequestEvidenceEvaluationService @Inject constructor(
    private val policyLoader: InformationRequestEvidencePolicyLoader,
    private val requirementRepository: InformationRequestRequirementRepository,
    private val substituteRepository: InformationRequestTemplateBindingSubstituteRepository,
    private val artifactRepository: InformationRequestEvidenceArtifactRepository,
    private val versionRepository: InformationRequestEvidenceVersionRepository,
    private val assessmentRepository: InformationRequestEvidenceAssessmentRepository,
    private val documentVersionRecordingService: DocumentVersionRecordingService,
    private val deploymentPolicy: InformationRequestEvidenceDeploymentPolicy,
    private val clock: Clock,
)
{
    fun evaluate(
        requirement: InformationRequestRequirement,
        disposition: InformationRequestResponseDisposition? = null,
    ): InformationRequestEvidenceRequirementEvaluation?
    {
        val policy = policyLoader.forBinding(requirement.sourceTemplateBindingId) ?: return null
        val asOf = LocalDate.now(clock)
        val malwareScanRequired = deploymentPolicy.malwareScanRequired()
        val substituteSatisfied = substituteOccurrences(requirement).any { substitute ->
            val substitutePolicy = policyLoader.forBinding(substitute.sourceTemplateBindingId) ?: return@any false
            InformationRequestEvidencePolicyEvaluator.evaluateRequirement(
                substitutePolicy,
                facts(substitute),
                asOf,
                malwareScanRequired = malwareScanRequired,
            )
                .state == InformationRequestEvidenceRequirementState.SATISFIED
        }
        return InformationRequestEvidencePolicyEvaluator.evaluateRequirement(
            policy,
            facts(requirement),
            asOf,
            substituteSatisfied = substituteSatisfied,
            disposition = disposition,
            malwareScanRequired = malwareScanRequired,
        )
    }

    fun submissionFacts(
        requirement: InformationRequestRequirement,
        disposition: InformationRequestResponseDisposition? = null,
    ): InformationRequestEvidenceSubmissionFacts?
    {
        val evaluation = evaluate(requirement, disposition) ?: return null
        val conformanceByVersion = evaluation.versions.associate { it.versionId to it.conformance }
        val versionsByArtifact = artifactRepository.findForRequirement(requirement.id)
            .filter { it.collectionState == InformationRequestEvidenceCollectionState.ACTIVE }
            .associateWith { versionRepository.findForArtifact(it.id) }
        val current = versionsByArtifact.mapNotNull { (artifact, versions) ->
            versions.maxByOrNull { it.versionNumber }?.let { artifact to it }
        }
        val assessments = assessmentRepository.findForVersions(current.map { it.second.id })
            .groupBy { it.evidenceVersionId }
        return InformationRequestEvidenceSubmissionFacts(
            state = evaluation.state,
            members = current.map { (artifact, version) ->
                val content = version.documentVersionId?.let(documentVersionRecordingService::findVersion)
                val governing = assessments[version.id].orEmpty()
                InformationRequestEvidenceSubmissionMember(
                    artifactId = artifact.id,
                    versionId = version.id,
                    versionNumber = version.versionNumber,
                    documentVersionId = content?.id,
                    contentHashAlgorithm = content?.contentHashAlgorithm?.name,
                    contentHash = content?.contentHash,
                    contentLength = content?.contentLength,
                    contentVerification = content?.contentVerification?.name,
                    conformance = conformanceByVersion[version.id] ?: InformationRequestEvidenceConformance.PENDING,
                    inspectionAssessmentId = InformationRequestEvidenceAssessmentSelection.latestInspection(governing)?.id,
                    malwareAssessmentId = InformationRequestEvidenceAssessmentSelection.governingScan(governing)?.id,
                )
            }.sortedWith(compareBy({ it.artifactId }, { it.versionNumber })),
        )
    }

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
        val content = (InformationRequestEvidenceVersionSourceMapper.read(version) as? InformationRequestDocumentVersionEvidenceSource)
            ?.let { documentVersionRecordingService.findVersion(it.documentVersionId) }

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
            fileBacked = version.documentVersionId != null,
            contentVerification = content?.contentVerification,
            contentLength = content?.contentLength,
            declaredMediaType = version.declaredMediaType,
            attributes = InformationRequestEvidenceAttributesMapper.read(version),
            inspection = InformationRequestEvidenceAssessmentSelection.latestInspection(assessments)?.inspectionFacts(),
            malware = InformationRequestEvidenceAssessmentSelection.governingScan(assessments)?.malwareFacts(),
        )
    }

    private fun substituteOccurrences(requirement: InformationRequestRequirement): List<InformationRequestRequirement>
    {
        val substituteBindings = substituteRepository.findForBinding(requirement.sourceTemplateBindingId)
            .map { it.substituteTemplateBindingId }
            .toSet()
        if (substituteBindings.isEmpty()) return emptyList()

        val candidates = requirementRepository.findForRequest(requirement.informationRequestId)
            .filter { it.sourceTemplateBindingId in substituteBindings && it.id != requirement.id }
        return candidates.filter { it.occurrencePath == requirement.occurrencePath }
            .ifEmpty { candidates.filter { it.occurrencePath == InformationRequestOccurrencePath.ROOT } }
    }
}
