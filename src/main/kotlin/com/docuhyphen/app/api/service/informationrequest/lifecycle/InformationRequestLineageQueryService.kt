package com.docuhyphen.app.api.service.informationrequest.lifecycle

import com.docuhyphen.app.api.model.entity.InformationRequestCarryForwardDecision
import com.docuhyphen.app.api.model.entity.InformationRequestLineageKind
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestCarryForwardOffer
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestLineageView
import com.docuhyphen.app.api.repository.informationrequest.lifecycle.InformationRequestCarryForwardRepository
import com.docuhyphen.app.api.repository.informationrequest.lifecycle.InformationRequestLineageRepository
import com.docuhyphen.app.api.repository.informationrequest.lifecycle.InformationRequestRecurrenceRepository
import com.docuhyphen.app.api.repository.informationrequest.submission.InformationRequestSubmissionItemRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.fields.FieldValueRevisionQueryService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestMutationGate
import com.docuhyphen.app.api.service.informationrequest.InformationRequestQueryService
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
