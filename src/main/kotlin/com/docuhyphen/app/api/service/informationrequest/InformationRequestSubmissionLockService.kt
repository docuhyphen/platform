package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestSubmissionPackage
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestSubmissionItemRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestSubmissionPackageRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestSubmissionWithdrawalRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.UUID

@ApplicationScoped
class InformationRequestSubmissionLockService @Inject constructor(
    private val packageRepository: InformationRequestSubmissionPackageRepository,
    private val itemRepository: InformationRequestSubmissionItemRepository,
    private val withdrawalRepository: InformationRequestSubmissionWithdrawalRepository,
    private val stages: InformationRequestSubmissionStages,
)
{
    fun activePackages(requestId: UUID): List<InformationRequestSubmissionPackage>
    {
        val withdrawn = withdrawalRepository.findForRequest(requestId).map { it.packageId }.toSet()
        return packageRepository.findForRequest(requestId).filterNot { it.id in withdrawn }
    }

    fun lockedRequirementIds(requestId: UUID): Set<UUID> =
        itemRepository.findForPackages(activePackages(requestId).map { it.id })
            .map { it.informationRequestRequirementId }
            .toSet()

    fun submittedStages(requestId: UUID): Set<String?> = activePackages(requestId).map { it.stageKey }.toSet()

    fun requireUnlocked(requestId: UUID, requirementIds: Collection<UUID>)
    {
        if (requirementIds.isEmpty()) return
        val locked = lockedRequirementIds(requestId)
        if (requirementIds.any { it in locked })
        {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.SUBMISSION_LOCKED,
                "This Information Request Requirement was submitted and cannot change until its submission is withdrawn",
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
}
