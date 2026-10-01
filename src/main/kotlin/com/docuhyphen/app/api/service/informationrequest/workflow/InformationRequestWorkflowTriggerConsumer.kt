package com.docuhyphen.app.api.service.informationrequest.workflow

import com.docuhyphen.app.api.exception.SubscriptionDenialException
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.informationrequest.workflow.InformationRequestWorkflowTriggerCatalog
import com.docuhyphen.app.api.model.notification.DomainEventConsumptionResult
import com.docuhyphen.app.api.service.notification.DomainEvent
import com.docuhyphen.app.api.service.notification.DomainEventConsumer
import com.docuhyphen.app.api.service.workflow.TriggerRequest
import com.docuhyphen.app.api.service.workflow.WorkflowEngineService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.*

@ApplicationScoped
class InformationRequestWorkflowTriggerConsumer @Inject constructor(
    private val workflowEngine: WorkflowEngineService,
) : DomainEventConsumer
{
    override val consumerKey: String = CONSUMER_KEY

    override fun handles(event: DomainEvent): Boolean =
        event.subject?.type == InformationRequestWorkflowTriggerCatalog.SUBJECT_RESOURCE_TYPE &&
                event.type in InformationRequestWorkflowTriggerCatalog.eventNames

    override fun consume(event: DomainEvent): DomainEventConsumptionResult
    {
        val subject = requireNotNull(event.subject)
        val request = TriggerRequest(
            triggerEvent = event.type,
            subjectResourceType = InformationRequestWorkflowTriggerCatalog.SUBJECT_RESOURCE_TYPE,
            subjectResourceId = UUID.fromString(subject.id),
            organizationId = event.organizationId?.let(UUID::fromString),
            subjectData = InformationRequestWorkflowSubject.of(event),
            initiatedByAppUserId = event.actor
                ?.takeIf { it.kind == PrincipalKind.USER.name }
                ?.let { runCatching { UUID.fromString(it.id) }.getOrNull() },
        )
        return try
        {
            val started = workflowEngine.trigger(request)
            DomainEventConsumptionResult.applied(started?.let { "WORKFLOW_STARTED:${it.instanceId}" }
                ?: NO_MATCHING_DEFINITION)
        }
        catch (denial: SubscriptionDenialException)
        {
            DomainEventConsumptionResult.skipped("SUBSCRIPTION_DENIED:${denial.denial.reason}")
        }
    }

    companion object
    {
        const val CONSUMER_KEY = "information-request-workflow-trigger"
        const val NO_MATCHING_DEFINITION = "NO_MATCHING_DEFINITION"
    }
}
