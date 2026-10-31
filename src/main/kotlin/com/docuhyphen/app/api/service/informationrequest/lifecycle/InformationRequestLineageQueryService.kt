package com.docuhyphen.app.api.service.informationrequest.lifecycle

import com.docuhyphen.app.api.model.entity.InformationRequestCarryForwardDecision
import com.docuhyphen.app.api.model.entity.InformationRequestRecurrence
import com.docuhyphen.app.api.model.entity.InformationRequestRefreshRule
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestCarryForwardOffer
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestLineageView
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestRefreshRuleStanding
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.lifecycle.InformationRequestCarryForwardRepository
import com.docuhyphen.app.api.repository.informationrequest.lifecycle.InformationRequestLineageRepository
import com.docuhyphen.app.api.repository.informationrequest.lifecycle.InformationRequestRecurrenceRepository
import com.docuhyphen.app.api.repository.informationrequest.lifecycle.InformationRequestRefreshRuleRepository
import com.docuhyphen.app.api.repository.informationrequest.submission.InformationRequestSubmissionItemRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.fields.FieldValueRevisionQueryService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestMutationGate
import com.docuhyphen.app.api.service.informationrequest.InformationRequestQueryService
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestAuthorActionService
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestRelatedRequestVisibility
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.time.Clock
import java.util.*

@ApplicationScoped
class InformationRequestLineageQueryService @Inject constructor(
    private val queryService: InformationRequestQueryService,
    private val lineageRepository: InformationRequestLineageRepository,
    private val recurrenceRepository: InformationRequestRecurrenceRepository,
    private val carryForwardRepository: InformationRequestCarryForwardRepository,
    private val itemRepository: InformationRequestSubmissionItemRepository,
    private val fieldValueRevisions: FieldValueRevisionQueryService,
    private val gate: InformationRequestMutationGate,
    private val refreshRuleRepository: InformationRequestRefreshRuleRepository,
    private val requestRepository: InformationRequestRepository,
    private val authorActions: InformationRequestAuthorActionService,
    private val clock: Clock,
)
{
    fun lineage(requestId: UUID, access: RequestAccessContext): InformationRequestLineageView
    {
        val request = queryService.findById(requestId, access)
        val visible = InformationRequestRelatedRequestVisibility.of(gate, access)
        val source = lineageRepository.findForSuccessor(request.id)
        val recurrence = recurrenceRepository.findForOrigin(request.id)
        val series = if (recurrence != null) null
        else source?.recurrenceId?.let(recurrenceRepository::findById)?.takeIf { visible(it.originRequestId) }
        val followed = recurrence ?: series
        val created = followed?.let { occurrencesCreated(it, visible) }
        val nextDue = followed?.let { schedule ->
            created?.let { InformationRequestRecurrenceSchedule.nextDueAt(schedule, it) }
        }
        return InformationRequestLineageView(
            request = request,
            source = source?.takeIf { visible(it.sourceRequestId) },
            successors = lineageRepository.findForSource(request.id).filter { visible(it.successorRequestId) },
            recurrence = recurrence,
            series = series,
            nextOccurrenceDueAt = nextDue,
            everyOccurrenceCreated = created != null && nextDue == null,
            nextOccurrenceCreatable = followed?.let { nextOccurrenceCreatable(it, access) } == true,
            refreshRules = visibleRefreshRules(request.id, access).map(::standingOf),
        )
    }

    private fun visibleRefreshRules(
        requestId: UUID,
        access: RequestAccessContext
    ): List<InformationRequestRefreshRule> {
        val rules = refreshRuleRepository.findForRequest(requestId)
        if (rules.isEmpty() || gate.permitsRequest(
                access,
                Action.INFORMATION_REQUEST_SUPERSEDE,
                requestId
            )
        ) return rules
        val requirements = refreshRuleRepository.findRequirementIdsByKey(requestId)
        return rules.filter { rule -> requirements[rule.requirementKey].orEmpty().any { permitsView(access, it) } }
    }

    private fun nextOccurrenceCreatable(
        recurrence: InformationRequestRecurrence,
        access: RequestAccessContext
    ): Boolean {
        val series = lineageRepository.findForRecurrence(recurrence.id)
        if (recurrence.maximumOccurrences?.let { series.size >= it } == true) return false
        val followedId = InformationRequestRecurrenceSchedule.followedRequestId(recurrence, series) { occurrenceId ->
            requestRepository.findById(occurrenceId)?.state
        }
        val followedRequest = requestRepository.findById(followedId) ?: return false
        return gate.permitsRequest(access, Action.INFORMATION_REQUEST_SUPERSEDE, recurrence.originRequestId) &&
                authorActions.createsFollowUp(followedRequest, access)
    }

    private fun standingOf(rule: InformationRequestRefreshRule): InformationRequestRefreshRuleStanding {
        val source = refreshRuleRepository.findRefreshSource(rule.informationRequestId, rule.requirementKey)
            ?: return InformationRequestRefreshRuleStanding(rule)
        val refreshed = lineageRepository.existsRefresh(rule.id, source.packageId)
        return InformationRequestRefreshRuleStanding(
            rule = rule,
            refreshDueOn = InformationRequestRefreshSchedule.dueOn(rule, source),
            refreshDue = !refreshed && InformationRequestRefreshSchedule.isDue(rule, source, clock.instant()),
            refreshed = refreshed,
        )
    }

    private fun occurrencesCreated(recurrence: InformationRequestRecurrence, visible: (UUID) -> Boolean): Int? =
        lineageRepository.findForRecurrence(recurrence.id)
            .takeIf { series -> series.all { visible(it.successorRequestId) } }
            ?.size

    fun carryForwards(requestId: UUID, access: RequestAccessContext): List<InformationRequestCarryForwardOffer>
    {
        queryService.findById(requestId, access)
        return carryForwardRepository.findForRequest(requestId)
            .filter { permitsView(access, it.informationRequestRequirementId) }
            .mapNotNull { carryForward ->
                val sourceItem = itemRepository.findById(carryForward.sourceItemId)
                    ?.takeIf { permitsView(access, it.informationRequestRequirementId) }
                    ?: return@mapNotNull null
                val offered =
                    sourceItem.takeIf { carryForward.decision == InformationRequestCarryForwardDecision.OFFERED }
                InformationRequestCarryForwardOffer(
                    carryForward = carryForward,
                    sourceItem = offered,
                    sourceFieldValue = offered?.fieldValueRevisionId?.let(fieldValueRevisions::valueOf),
                )
            }
    }

    private fun permitsView(access: RequestAccessContext, requirementId: UUID): Boolean =
        gate.permitsRequirement(access, Action.INFORMATION_REQUEST_REQUIREMENT_VIEW, requirementId)
}
