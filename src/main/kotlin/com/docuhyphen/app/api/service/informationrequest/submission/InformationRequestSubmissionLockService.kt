package com.docuhyphen.app.api.service.informationrequest.submission

import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.InformationRequestItemProblem
import com.docuhyphen.app.api.model.informationrequest.access.InformationRequestRequirementCorrectionScope
import com.docuhyphen.app.api.model.informationrequest.response.InformationRequestCompletenessItemState
import com.docuhyphen.app.api.model.informationrequest.submission.InformationRequestLockedRequirement
import com.docuhyphen.app.api.model.informationrequest.submission.InformationRequestResponseEditLock
import com.docuhyphen.app.api.model.informationrequest.submission.InformationRequestReturnedItem
import com.docuhyphen.app.api.model.informationrequest.submission.InformationRequestSubmittedScope
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.review.InformationRequestCorrectionEvidenceRepository
import com.docuhyphen.app.api.repository.informationrequest.review.InformationRequestCorrectionItemRepository
import com.docuhyphen.app.api.repository.informationrequest.review.InformationRequestCorrectionRepository
import com.docuhyphen.app.api.repository.informationrequest.submission.*
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateRequirementBindingRepository
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestItemRefusalException
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
    private val requirementRepository: InformationRequestRequirementRepository,
    private val bindingRepository: InformationRequestTemplateRequirementBindingRepository,
    private val packageAttestationRepository: InformationRequestSubmissionPackageAttestationRepository,
    private val attestationRepository: InformationRequestSubmissionAttestationRepository,
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

    fun laterSubmittedStages(request: InformationRequest, stageKey: String?): List<String> {
        stageKey ?: return emptyList()
        if (stages.versionOf(request).submissionStageOrdering != InformationRequestSubmissionStageOrdering.SEQUENTIAL) {
            return emptyList()
        }
        val submitted = submittedStages(request.id)
        return stages.stageOrder(request).dropWhile { it != stageKey }.drop(1).filter { it in submitted }
    }

    fun openCorrections(requestId: UUID): List<InformationRequestCorrection>
    {
        val active = activePackages(requestId).map { it.id }.toSet()
        return correctionRepository.findOpenForRequest(requestId).filter { it.packageId in active }
    }

    fun returnedItems(requestId: UUID): List<InformationRequestReturnedItem> {
        val corrections = openCorrections(requestId)
        if (corrections.isEmpty()) return emptyList()
        val submittedById = itemRepository.findForPackages(corrections.map { it.packageId }).associateBy { it.id }
        return correctionItemRepository.findForCorrections(corrections.map { it.id })
            .mapNotNull { returned -> submittedById[returned.submissionItemId] }
            .distinctBy { it.informationRequestRequirementId }
            .map { item ->
                InformationRequestReturnedItem(
                    requirementId = item.informationRequestRequirementId,
                    requirementType = item.requirementType,
                    submittedResponseRevision = item.responseRevision,
                )
            }
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
        val reopened = scope.returned(item) ||
                item.requirementType == InformationRequestRequirementType.RESPONSE_ATTESTATION ||
                (scope.editable(item) && answeredOnce(requestId, listOf(requirementId)).isEmpty())
        return if (reopened)
            InformationRequestRequirementCorrectionScope.CORRECTION_ALLOWED
        else
            InformationRequestRequirementCorrectionScope.CORRECTION_EXCLUDED
    }

    fun lockedRequirementIds(requestId: UUID, requirementIds: Collection<UUID>): Set<UUID> =
        lockedRequirements(requestId, requirementIds).map { it.requirementId }.toSet()

    fun editLocks(requestId: UUID, requirementIds: Collection<UUID>): Map<UUID, InformationRequestResponseEditLock> =
        lockedRequirements(requestId, requirementIds).associate { it.requirementId to it.lock }

    fun requireUnlocked(requestId: UUID, requirementIds: Collection<UUID>)
    {
        val problems = unlockProblems(requestId, requirementIds)
        if (problems.isEmpty()) return
        throw InformationRequestItemRefusalException(
            reasonCode = problems.first().code,
            message = "A Requirement this change addresses is locked by its submission",
            problems = problems,
        )
    }

    private fun unlockProblems(requestId: UUID, requirementIds: Collection<UUID>): List<InformationRequestItemProblem> =
        lockedRequirements(requestId, requirementIds).map { locked ->
            when (locked.lock) {
                InformationRequestResponseEditLock.GIVEN_ONCE -> InformationRequestItemProblem(
                    code = InformationRequestErrorCatalog.SUBMISSION_LOCKED,
                    message = "This answer is given once and cannot change after it was submitted",
                    requirementId = locked.requirementId,
                    occurrencePath = locked.occurrencePath,
                )

                InformationRequestResponseEditLock.NOT_RETURNED_FOR_CORRECTION -> InformationRequestItemProblem(
                    code = InformationRequestErrorCatalog.CORRECTION_SCOPE_DENIED,
                    message = "This Requirement was not returned for correction",
                    requirementId = locked.requirementId,
                    occurrencePath = locked.occurrencePath,
                )

                InformationRequestResponseEditLock.SUBMITTED -> InformationRequestItemProblem(
                    code = InformationRequestErrorCatalog.SUBMISSION_LOCKED,
                    message = "This Requirement was submitted and cannot change until its submission is withdrawn",
                    requirementId = locked.requirementId,
                    occurrencePath = locked.occurrencePath,
                )
            }
        }

    private fun lockedRequirements(
        requestId: UUID,
        requirementIds: Collection<UUID>,
    ): List<InformationRequestLockedRequirement> {
        if (requirementIds.isEmpty()) return emptyList()
        val scope = scopeOf(requestId)
        val answeredOnce = answeredOnce(requestId, requirementIds)
        return requirementIds.distinct().mapNotNull { requirementId ->
            val givenOnce = answeredOnce[requirementId]?.let { occurrencePath ->
                InformationRequestLockedRequirement(
                    requirementId,
                    occurrencePath,
                    InformationRequestResponseEditLock.GIVEN_ONCE
                )
            }
            val item = scope.items.firstOrNull { it.informationRequestRequirementId == requirementId }
                ?: return@mapNotNull givenOnce
            when {
                scope.returned(item) -> null
                scope.editable(item) -> givenOnce
                item.packageId in scope.correctionByPackage -> InformationRequestLockedRequirement(
                    requirementId,
                    item.occurrencePath,
                    InformationRequestResponseEditLock.NOT_RETURNED_FOR_CORRECTION,
                )

                else -> InformationRequestLockedRequirement(
                    requirementId,
                    item.occurrencePath,
                    InformationRequestResponseEditLock.SUBMITTED,
                )
            }
        }
    }

    fun requireNotGivenOnce(requestId: UUID, requirementIds: Collection<UUID>) {
        if (requirementIds.isEmpty()) return
        val scope = scopeOf(requestId)
        val returned = scope.items.filter { scope.returned(it) }.map { it.informationRequestRequirementId }.toSet()
        val problems =
            answeredOnce(requestId, requirementIds.filter { it !in returned }).map { (requirementId, occurrencePath) ->
                InformationRequestItemProblem(
                    code = InformationRequestErrorCatalog.SUBMISSION_LOCKED,
                    message = "This answer is given once and cannot be removed after it was submitted",
                    requirementId = requirementId,
                    occurrencePath = occurrencePath,
                )
            }
        if (problems.isEmpty()) return
        throw InformationRequestItemRefusalException(
            reasonCode = InformationRequestErrorCatalog.SUBMISSION_LOCKED,
            message = "This change would remove an answer given once",
            problems = problems,
        )
    }

    fun givenOnceRequirementIds(requestId: UUID): Set<UUID> {
        val scope = scopeOf(requestId)
        val returned = scope.items.filter { scope.returned(it) }.map { it.informationRequestRequirementId }.toSet()
        return givenOnce(requestId, requirementRepository.findForRequest(requestId).map { it.id })
            .map { it.informationRequestRequirementId }
            .filterNot { it in returned }
            .toSet()
    }

    fun requireAttestationDecisionOpen(
        requestId: UUID,
        requirementId: UUID,
        role: InformationRequestContributorRole,
        decision: InformationRequestAttestationDecision,
    ) {
        val scope = scopeOf(requestId)
        if (scope.items.any { it.informationRequestRequirementId == requirementId && scope.returned(it) }) return
        val held = givenOnce(requestId, listOf(requirementId))
        if (held.isEmpty()) return
        val frozen = packageAttestationRepository.findForPackages(held.map { it.packageId }.toSet())
            .map { it.attestationId }
            .toSet()
        val given = attestationRepository.findForRequest(requestId)
            .filter { it.id in frozen && it.attestationRequirementId == requirementId && it.partyRole == role }
            .map { it.decision }
            .toSet()
        if (decision in given) return
        throw InformationRequestLifecycleException(
            InformationRequestErrorCatalog.SUBMISSION_LOCKED,
            "This confirmation is given once and keeps the decision it was submitted with",
        )
    }

    private fun answeredOnce(requestId: UUID, requirementIds: Collection<UUID>): Map<UUID, String> =
        givenOnce(requestId, requirementIds).associate { it.informationRequestRequirementId to it.occurrencePath }

    private fun givenOnce(requestId: UUID, requirementIds: Collection<UUID>): List<InformationRequestSubmissionItem> {
        val requested = requirementIds.toSet()
        if (requested.isEmpty()) return emptyList()
        val modes = mutableMapOf<UUID, InformationRequestResponseMode?>()
        val provideOnce = requirementRepository.findForRequest(requestId)
            .filter { requirement ->
                requirement.id in requested &&
                        modes.getOrPut(requirement.sourceTemplateBindingId) {
                            bindingRepository.findById(requirement.sourceTemplateBindingId)?.responseMode
                        } == InformationRequestResponseMode.PROVIDE_ONCE
            }
            .map { it.id }
            .toSet()
        if (provideOnce.isEmpty()) return emptyList()
        return itemRepository.findForPackages(packageRepository.findForRequest(requestId).map { it.id })
            .filter {
                it.informationRequestRequirementId in provideOnce &&
                        it.completenessState != InformationRequestCompletenessItemState.HIDDEN
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

    fun evidenceArtifactOpen(requestId: UUID, requirementId: UUID, artifactId: UUID): Boolean =
        lockedRequirementIds(requestId, listOf(requirementId)).isEmpty() &&
                !frozenOutsideCorrection(requestId, requirementId, artifactId)

    fun requireEvidenceArtifactOpen(requestId: UUID, requirementId: UUID, artifactId: UUID)
    {
        requireUnlocked(requestId, listOf(requirementId))
        if (frozenOutsideCorrection(requestId, requirementId, artifactId)) {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.CORRECTION_SCOPE_DENIED,
                "Only a file the correction returned can be replaced or withdrawn",
            )
        }
    }

    private fun frozenOutsideCorrection(requestId: UUID, requirementId: UUID, artifactId: UUID): Boolean {
        val scope = scopeOf(requestId)
        val item = scope.items.firstOrNull { it.informationRequestRequirementId == requirementId } ?: return false
        val correction = scope.correctionByPackage[item.packageId] ?: return false
        val frozen = evidenceRepository.findForPackages(listOf(item.packageId))
            .filter { it.itemId == item.id && it.evidenceArtifactId == artifactId }
        if (frozen.isEmpty()) return false
        val returned =
            correctionEvidenceRepository.findForCorrections(listOf(correction.id)).map { it.evidenceVersionId }.toSet()
        return frozen.none { it.evidenceVersionId in returned }
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
