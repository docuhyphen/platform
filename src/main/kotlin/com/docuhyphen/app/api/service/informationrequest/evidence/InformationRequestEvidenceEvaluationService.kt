package com.docuhyphen.app.api.service.informationrequest.evidence

import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceCollectionState
import com.docuhyphen.app.api.model.entity.InformationRequestRequirement
import com.docuhyphen.app.api.model.entity.InformationRequestResponseDisposition
import com.docuhyphen.app.api.model.informationrequest.evidence.InformationRequestEvidenceConformance
import com.docuhyphen.app.api.model.informationrequest.evidence.InformationRequestEvidenceRequirementEvaluation
import com.docuhyphen.app.api.model.informationrequest.evidence.InformationRequestEvidenceRequirementState
import com.docuhyphen.app.api.model.informationrequest.evidence.InformationRequestEvidenceVersionFacts
import com.docuhyphen.app.api.model.informationrequest.submission.InformationRequestEvidenceSubmissionFacts
import com.docuhyphen.app.api.model.informationrequest.submission.InformationRequestEvidenceSubmissionMember
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.evidence.InformationRequestEvidenceArtifactRepository
import com.docuhyphen.app.api.repository.informationrequest.evidence.InformationRequestEvidenceAssessmentRepository
import com.docuhyphen.app.api.repository.informationrequest.evidence.InformationRequestEvidenceVersionRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateBindingSubstituteRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateRequirementBindingRepository
import com.docuhyphen.app.api.service.document.DocumentVersionRecordingService
import com.docuhyphen.app.api.service.informationrequest.condition.InformationRequestConditionEvaluationService
import com.docuhyphen.app.api.service.informationrequest.condition.InformationRequestGoverningCondition
import com.docuhyphen.app.api.service.informationrequest.occurrence.InformationRequestOccurrencePath
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.time.Clock
import java.time.LocalDate
import java.util.*

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
    private val bindingRepository: InformationRequestTemplateRequirementBindingRepository,
    private val conditionEvaluationService: InformationRequestConditionEvaluationService,
    private val clock: Clock,
    private val factsReader: InformationRequestEvidenceFactsReader,
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
                val content = documentVersionRecordingService.findVersion(version.documentVersionId)
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

    fun facts(requirement: InformationRequestRequirement): List<InformationRequestEvidenceVersionFacts> =
        factsReader.facts(requirement)

    private fun substituteOccurrences(requirement: InformationRequestRequirement): List<InformationRequestRequirement>
    {
        val substituteBindings = substituteRepository.findForBinding(requirement.sourceTemplateBindingId)
            .map { it.substituteTemplateBindingId }
            .toSet()
        if (substituteBindings.isEmpty()) return emptyList()

        val candidates = requirementRepository.findForRequest(requirement.informationRequestId)
            .filter { it.sourceTemplateBindingId in substituteBindings && it.id != requirement.id }
        val occurrences = candidates.filter { it.occurrencePath == requirement.occurrencePath }
            .ifEmpty { candidates.filter { it.occurrencePath == InformationRequestOccurrencePath.ROOT } }
        return applicable(requirement.informationRequestId, occurrences)
    }

    private fun applicable(
        requestId: UUID,
        substitutes: List<InformationRequestRequirement>,
    ): List<InformationRequestRequirement> {
        val governed = substitutes
            .associate { it.id to bindingRepository.findById(it.sourceTemplateBindingId) }
            .filterValues { InformationRequestGoverningCondition.ruleKeyOf(it) != null }
        if (governed.isEmpty()) return substitutes

        val conditions = InformationRequestGoverningCondition.byScope(conditionEvaluationService.evaluate(requestId))
        return substitutes.filterNot { substitute ->
            governed[substitute.id]?.let {
                InformationRequestGoverningCondition.hides(it, substitute.occurrencePath, conditions)
            } == true
        }
    }
}
