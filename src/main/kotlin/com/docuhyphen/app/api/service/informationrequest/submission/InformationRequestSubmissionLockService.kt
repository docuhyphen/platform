package com.docuhyphen.app.api.service.informationrequest.submission

import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestCorrection
import com.docuhyphen.app.api.model.entity.InformationRequestRequirementType
import com.docuhyphen.app.api.model.entity.InformationRequestSubmissionPackage
import com.docuhyphen.app.api.model.informationrequest.access.InformationRequestRequirementCorrectionScope
import com.docuhyphen.app.api.model.informationrequest.submission.InformationRequestSubmittedScope
import com.docuhyphen.app.api.repository.informationrequest.review.InformationRequestCorrectionEvidenceRepository
import com.docuhyphen.app.api.repository.informationrequest.review.InformationRequestCorrectionItemRepository
import com.docuhyphen.app.api.repository.informationrequest.review.InformationRequestCorrectionRepository
import com.docuhyphen.app.api.repository.informationrequest.submission.InformationRequestSubmissionEvidenceRepository
import com.docuhyphen.app.api.repository.informationrequest.submission.InformationRequestSubmissionItemRepository
import com.docuhyphen.app.api.repository.informationrequest.submission.InformationRequestSubmissionPackageRepository
import com.docuhyphen.app.api.repository.informationrequest.submission.InformationRequestSubmissionWithdrawalRepository
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.*

@ApplicationScoped
class InformationRequestSubmissionLockService @Inject constructor(
    private val packageRepository: InformationRequestSubmissionPackageRepository,
    private val itemRepository: InformationRequestSubmissionItemRepository,
    private val evidenceRepository: InformationRequestSubmissionEvidenceRepository,
    private val withdrawalRepository: InformationRequestSubmissionWithdrawalRepository,
    private val correctionRepository: InformationRequestCorrectionRepository,
    private val correctionItemRepository: InformationRequestCorrectionItemRepository,
    private val correctionEvidenceRepository: InformationRequestCorrectionEvidenceRepository,
    private val stages: InformationRequestSubmissionStages,
)
{
    fun activePackages(requestId: UUID): List<InformationRequestSubmissionPackage>
    {
        val packages = packageRepository.findForRequest(requestId)
        val withdrawn = withdrawalRepository.findForRequest(requestId).map { it.packageId }.toSet()
        val followed = packages.mapNotNull { it.previousPackageId }.toSet()
        return packages.filterNot { it.id in withdrawn || it.id in followed }
    }

    fun submittedRequirementIds(requestId: UUID): Set<UUID> =
        itemRepository.findForPackages(activePackages(requestId).map { it.id })
            .map { it.informationRequestRequirementId }
            .toSet()

    fun submittedStages(requestId: UUID): Set<String?> = activePackages(requestId).map { it.stageKey }.toSet()

    fun openCorrections(requestId: UUID): List<InformationRequestCorrection>
    {
        val active = activePackages(requestId).map { it.id }.toSet()
        return correctionRepository.findOpenForRequest(requestId).filter { it.packageId in active }
    }

    fun openCorrectionForStage(requestId: UUID, stageKey: String?): InformationRequestCorrection?
    {
        val scopePackage = activePackages(requestId).firstOrNull { it.stageKey == stageKey } ?: return null
        return openCorrections(requestId).firstOrNull { it.packageId == scopePackage.id }
    }

    fun correctionScopeOf(requestId: UUID, requirementId: UUID): InformationRequestRequirementCorrectionScope
    {
        val scope = scopeOf(requestId)
        val item = scope.items.firstOrNull { it.informationRequestRequirementId == requirementId }
            ?: return InformationRequestRequirementCorrectionScope.NORMAL_RESPONSE
        if (item.packageId !in scope.correctionByPackage) return InformationRequestRequirementCorrectionScope.NORMAL_RESPONSE
        return if (scope.editable(item) || item.requirementType == InformationRequestRequirementType.RESPONSE_ATTESTATION)
            InformationRequestRequirementCorrectionScope.CORRECTION_ALLOWED
        else
            InformationRequestRequirementCorrectionScope.CORRECTION_EXCLUDED
    }

    fun requireUnlocked(requestId: UUID, requirementIds: Collection<UUID>)
    {
        if (requirementIds.isEmpty()) return
        val scope = scopeOf(requestId)
        requirementIds.forEach { requirementId ->
            val item = scope.items.firstOrNull { it.informationRequestRequirementId == requirementId } ?: return@forEach
            if (scope.editable(item)) return@forEach
            if (item.packageId in scope.correctionByPackage)
            {
                throw InformationRequestLifecycleException(
                    InformationRequestErrorCatalog.CORRECTION_SCOPE_DENIED,
                    "This Information Request Requirement was not returned for correction",
                )
            }
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.SUBMISSION_LOCKED,
                "This Information Request Requirement was submitted and cannot change until its submission is withdrawn",
            )
        }
    }

    fun requireAttestationOpen(requestId: UUID, requirementId: UUID)
    {
        val scope = scopeOf(requestId)
        val item = scope.items.firstOrNull { it.informationRequestRequirementId == requirementId } ?: return
        if (item.packageId in scope.correctionByPackage) return
        throw InformationRequestLifecycleException(
            InformationRequestErrorCatalog.SUBMISSION_LOCKED,
            "This Information Request Requirement was submitted and cannot change until its submission is withdrawn",
        )
    }

    fun requireEvidenceArtifactOpen(requestId: UUID, requirementId: UUID, artifactId: UUID)
    {
        requireUnlocked(requestId, listOf(requirementId))
        val scope = scopeOf(requestId)
        val item = scope.items.firstOrNull { it.informationRequestRequirementId == requirementId } ?: return
        val correction = scope.correctionByPackage[item.packageId] ?: return
        val frozen = evidenceRepository.findForPackages(listOf(item.packageId))
            .filter { it.itemId == item.id && it.evidenceArtifactId == artifactId }
        if (frozen.isEmpty()) return
        val returned =
            correctionEvidenceRepository.findForCorrections(listOf(correction.id)).map { it.evidenceVersionId }.toSet()
        if (frozen.none { it.evidenceVersionId in returned })
        {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.CORRECTION_SCOPE_DENIED,
                "Only a file the correction returned can be replaced or withdrawn",
            )
        }
    }

    fun requireBindingsOpen(request: InformationRequest, bindingIds: Collection<UUID>)
    {
        stages.scopesOf(request, bindingIds).forEach { requireScopeOpen(request.id, it) }
    }

    fun requireScopeOpen(requestId: UUID, stageKey: String?)
    {
        val submitted = submittedStages(requestId)
        if (stageKey in submitted || (stageKey != null && null in submitted))
        {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.SUBMISSION_LOCKED,
                "This part of the Information Request was submitted and cannot change until its submission is withdrawn",
            )
        }
    }

    private fun scopeOf(requestId: UUID): InformationRequestSubmittedScope
    {
        val active = activePackages(requestId)
        val items = itemRepository.findForPackages(active.map { it.id })
        val corrections = correctionRepository.findOpenForRequest(requestId)
            .filter { correction -> active.any { it.id == correction.packageId } }
        val allowlisted = correctionItemRepository.findForCorrections(corrections.map { it.id })
            .groupBy({ it.correctionId }, { it.requirementId })
            .mapValues { it.value.toSet() }
        return InformationRequestSubmittedScope(
            items = items,
            correctionByPackage = corrections.associateBy { it.packageId },
            allowlistedByCorrection = allowlisted,
        )
    }
}
