package com.docuhyphen.app.api.service.informationrequest.submission

import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestShareRoleKey
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestRequirementAuthorizationContextProvider
import com.docuhyphen.app.api.service.informationrequest.condition.InformationRequestConditionVisibility
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject

@ApplicationScoped
class InformationRequestRespondentWork @Inject constructor(
    private val lockService: InformationRequestSubmissionLockService,
    private val stages: InformationRequestSubmissionStages,
    private val requirementRepository: InformationRequestRequirementRepository,
    private val conditionVisibility: InformationRequestConditionVisibility,
    private val requirementRoles: InformationRequestRequirementAuthorizationContextProvider,
) {
    fun completelySubmitted(request: InformationRequest): Boolean {
        val submitted = lockService.submittedStages(request.id)
        val order = stages.stageOrder(request)
        val everyStage = if (order.isEmpty()) submitted.isNotEmpty() else submitted.containsAll(order)
        return everyStage && lockService.openCorrections(request.id).isEmpty()
    }

    fun rolesWithOutstandingItems(request: InformationRequest): Set<InformationRequestShareRoleKey> {
        if (completelySubmitted(request)) return emptySet()
        val submitted = lockService.submittedRequirementIds(request.id)
        val returned = lockService.returnedItems(request.id).map { it.requirementId }.toSet()
        val outstanding =
            requirementRepository.findForRequest(request.id).filter { it.id !in submitted || it.id in returned }
        val hidden = conditionVisibility.hiddenAmong(request, outstanding).map { it.id }.toSet()
        return outstanding.filter { it.id !in hidden }.flatMap { requirementRoles.answeringRoleKeysOf(it.id) }.toSet()
    }
}
