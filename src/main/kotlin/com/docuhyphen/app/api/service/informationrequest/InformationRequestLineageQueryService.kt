package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequestCarryForwardDecision
import com.docuhyphen.app.api.model.entity.InformationRequestLineageKind
import com.docuhyphen.app.api.model.informationrequest.InformationRequestCarryForwardOffer
import com.docuhyphen.app.api.model.informationrequest.InformationRequestLineageView
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestCarryForwardRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestLineageRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRecurrenceRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestSubmissionItemRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.fields.FieldValueRevisionQueryService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.UUID

@ApplicationScoped
class InformationRequestLineageQueryService @Inject constructor(
    private val queryService: InformationRequestQueryService,
    private val lineageRepository: InformationRequestLineageRepository,
    private val recurrenceRepository: InformationRequestRecurrenceRepository,
    private val carryForwardRepository: InformationRequestCarryForwardRepository,
    private val itemRepository: InformationRequestSubmissionItemRepository,
    private val fieldValueRevisions: FieldValueRevisionQueryService,
    private val gate: InformationRequestMutationGate,
)
{
    fun lineage(requestId: UUID, access: RequestAccessContext): InformationRequestLineageView
    {
        val request = queryService.findById(requestId, access)
        val successors = lineageRepository.findForSource(request.id)
        val recurrence = recurrenceRepository.findForOrigin(request.id)
        return InformationRequestLineageView(
            request = request,
            source = lineageRepository.findForSuccessor(request.id),
            successors = successors,
            recurrence = recurrence,
            nextOccurrenceDueAt = recurrence?.let { schedule ->
                InformationRequestRecurrenceSchedule.nextDueAt(
                    schedule,
                    successors.count { it.lineageKind == InformationRequestLineageKind.RECURRENCE },
                )
            },
        )
    }

    fun carryForwards(requestId: UUID, access: RequestAccessContext): List<InformationRequestCarryForwardOffer>
    {
        queryService.findById(requestId, access)
        return carryForwardRepository.findForRequest(requestId)
            .filter { gate.permitsRequirement(access, Action.INFORMATION_REQUEST_REQUIREMENT_VIEW, it.informationRequestRequirementId) }
            .map { carryForward ->
                val item = carryForward.takeIf { it.decision == InformationRequestCarryForwardDecision.OFFERED }
                    ?.let { itemRepository.findById(it.sourceItemId) }
                InformationRequestCarryForwardOffer(
                    carryForward = carryForward,
                    sourceItem = item,
                    sourceFieldValue = item?.fieldValueRevisionId?.let(fieldValueRevisions::valueOf),
                )
            }
    }
}
