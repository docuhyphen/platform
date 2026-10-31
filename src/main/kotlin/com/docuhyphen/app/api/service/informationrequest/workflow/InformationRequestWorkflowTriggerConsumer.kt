package com.docuhyphen.app.api.service.informationrequest.workflow

import com.docuhyphen.app.api.exception.SubscriptionDenialException
import com.docuhyphen.app.api.exception.WorkflowStartRefusedException
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.informationrequest.workflow.InformationRequestWorkflowTriggerCatalog
import com.docuhyphen.app.api.model.notification.DomainEventConsumptionResult
import com.docuhyphen.app.api.model.workflow.TriggerRequest
import com.docuhyphen.app.api.service.informationrequest.InformationRequestQueryService
import com.docuhyphen.app.api.service.notification.DomainEvent
import com.docuhyphen.app.api.service.notification.DomainEventConsumer
import com.docuhyphen.app.api.service.workflow.WorkflowEngineService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.*

@ApplicationScoped
class InformationRequestWorkflowTriggerConsumer @Inject constructor(
    private val workflowEngine: WorkflowEngineService,
    private val queryService: InformationRequestQueryService,
) : DomainEventConsumer
{
    override val consumerKey: String = CONSUMER_KEY

    override fun handles(event: DomainEvent): Boolean =
        event.subject?.type == InformationRequestWorkflowTriggerCatalog.SUBJECT_RESOURCE_TYPE &&
                event.type in InformationRequestWorkflowTriggerCatalog.eventNames

    override fun consume(event: DomainEvent): DomainEventConsumptionResult
    {
        val subject = requireNotNull(event.subject)
        val requestId = UUID.fromString(subject.id)
        val organizationId = event.organizationId?.let(UUID::fromString)
        val request = TriggerRequest(
            triggerEvent = event.type,
            subjectResourceType = InformationRequestWorkflowTriggerCatalog.SUBJECT_RESOURCE_TYPE,
            subjectResourceId = requestId,
            organizationId = organizationId,
            subjectData = InformationRequestWorkflowSubject.of(event),
            initiatedByAppUserId = event.actor
                ?.takeIf { it.kind == PrincipalKind.USER.name }
                ?.let { runCatching { UUID.fromString(it.id) }.getOrNull() },
            ownerAppUserId = if (organizationId == null) queryService.ownerOf(requestId)?.userId else null,
        )
        if (event.type in ENDING_EVENTS) {
            workflowEngine.cancelActiveForSubject(
                InformationRequestWorkflowTriggerCatalog.SUBJECT_RESOURCE_TYPE,
                requestId,
                event.type
            )
        }
        return try
        {
            val started = workflowEngine.trigger(request)
            DomainEventConsumptionResult.applied(started?.let { "WORKFLOW_STARTED:${it.instanceId}" }
                ?: NO_MATCHING_DEFINITION)
        }
        catch (denial: SubscriptionDenialException)
        {
            DomainEventConsumptionResult.skipped("SUBSCRIPTION_DENIED:${denial.denial.reason}")
        } catch (refusal: WorkflowStartRefusedException) {
            DomainEventConsumptionResult.skipped("WORKFLOW_START_REFUSED:${refusal.definitionId}:${refusal.reasonCode}")
        }
    }

    companion object
    {
        const val CONSUMER_KEY = "information-request-workflow-trigger"
        const val NO_MATCHING_DEFINITION = "NO_MATCHING_DEFINITION"
        private val ENDING_EVENTS = setOf(
            "information_request.request.cancel",
            "information_request.request.supersede",
            "information_request.request.expire",
        )
    }
}
