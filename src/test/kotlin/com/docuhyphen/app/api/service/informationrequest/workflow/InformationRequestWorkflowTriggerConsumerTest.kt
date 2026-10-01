package com.docuhyphen.app.api.service.informationrequest.workflow

import com.docuhyphen.app.api.exception.SubscriptionDenialException
import com.docuhyphen.app.api.model.notification.DomainEventConsumptionOutcome
import com.docuhyphen.app.api.service.notification.DomainEvent
import com.docuhyphen.app.api.service.subscription.PlanCode
import com.docuhyphen.app.api.service.subscription.PlanFeature
import com.docuhyphen.app.api.service.subscription.SubscriptionDenial
import com.docuhyphen.app.api.service.subscription.SubscriptionDenialReason
import com.docuhyphen.app.api.service.subscription.SubscriptionOwnerType
import com.docuhyphen.app.api.service.workflow.TriggerRequest
import com.docuhyphen.app.api.service.workflow.TriggerResult
import com.docuhyphen.app.api.service.workflow.WorkflowEngineService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

class InformationRequestWorkflowTriggerConsumerTest
{
    private val engine = mock<WorkflowEngineService>()
    private val consumer = InformationRequestWorkflowTriggerConsumer(engine)
    private val requestId = UUID.randomUUID()
    private val organizationId = UUID.randomUUID()
    private val userId = UUID.randomUUID()

    @Test
    fun `only registered request triggers about a request subject are consumed`()
    {
        assertTrue(consumer.handles(event("information_request.request.submit")))
        assertTrue(consumer.handles(event("information_request.request.overdue")))
        assertFalse(consumer.handles(event("information_request.review.assign")))
        assertFalse(consumer.handles(event("information_request.request.submit").copy(subject = DomainEvent.SubjectRef("EXCHANGE", requestId.toString()))))
    }

    @Test
    fun `a submission starts matching workflows with only the safe fields its subject schema names`()
    {
        val packageId = UUID.randomUUID()
        whenever(engine.trigger(any())).thenReturn(TriggerResult(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), emptyList()))

        val result = consumer.consume(
            event(
                "information_request.request.submit",
                mapOf(
                    "submissionPackageId" to packageId.toString(),
                    "submissionPackageNumber" to "2",
                    "contentHash" to "abc",
                    "reasonCode" to "not-for-this-event",
                ),
            ),
        )

        assertEquals(DomainEventConsumptionOutcome.APPLIED, result.outcome)
        val captor = argumentCaptor<TriggerRequest>()
        verify(engine).trigger(captor.capture())
        val trigger = captor.firstValue
        assertEquals("information_request.request.submit", trigger.triggerEvent)
        assertEquals("INFORMATION_REQUEST", trigger.subjectResourceType)
        assertEquals(requestId, trigger.subjectResourceId)
        assertEquals(organizationId, trigger.organizationId)
        assertEquals(userId, trigger.initiatedByAppUserId)
        assertEquals(
            mapOf(
                "requestId" to requestId.toString(),
                "exchangeId" to EXCHANGE_ID,
                "templateVersionId" to TEMPLATE_VERSION_ID,
                "orgId" to organizationId.toString(),
                "state" to "IN_PROGRESS",
                "transitionSequence" to "4",
                "submissionPackageId" to packageId.toString(),
                "packageNumber" to "2",
            ),
            trigger.subjectData,
        )
    }

    @Test
    fun `a closure names the satisfying package and an unmatched trigger is still one recorded application`()
    {
        val packageId = UUID.randomUUID()
        whenever(engine.trigger(any())).thenReturn(null)

        val result = consumer.consume(event("information_request.request.close", mapOf("satisfiedByPackageId" to packageId.toString())))

        assertEquals(DomainEventConsumptionOutcome.APPLIED, result.outcome)
        assertEquals(InformationRequestWorkflowTriggerConsumer.NO_MATCHING_DEFINITION, result.detail)
        val captor = argumentCaptor<TriggerRequest>()
        verify(engine).trigger(captor.capture())
        assertEquals(packageId.toString(), captor.firstValue.subjectData["submissionPackageId"])
    }

    @Test
    fun `a subscription refusal is a skipped consumption rather than a retried failure`()
    {
        whenever(engine.trigger(any())).thenThrow(
            SubscriptionDenialException(
                SubscriptionDenial(
                    reason = SubscriptionDenialReason.FEATURE_NOT_INCLUDED,
                    planCode = PlanCode.PERSONAL,
                    ownerType = SubscriptionOwnerType.ORGANIZATION,
                    feature = PlanFeature.WORKFLOW_AUTOMATION,
                    message = "Workflow automation is not included",
                ),
            ),
        )

        val result = consumer.consume(event("information_request.request.issue"))

        assertEquals(DomainEventConsumptionOutcome.SKIPPED, result.outcome)
        assertEquals("SUBSCRIPTION_DENIED:FEATURE_NOT_INCLUDED", result.detail)
    }

    private fun event(type: String, extra: Map<String, String> = emptyMap()) = DomainEvent(
        type = type,
        actor = DomainEvent.PrincipalRefDto("USER", userId.toString()),
        subject = DomainEvent.SubjectRef("INFORMATION_REQUEST", requestId.toString()),
        organizationId = organizationId.toString(),
        payload = mapOf(
            "transitionId" to UUID.randomUUID().toString(),
            "sequenceNumber" to "4",
            "toState" to "IN_PROGRESS",
            "mutation" to "SUBMIT",
            "exchangeId" to EXCHANGE_ID,
            "templateVersionId" to TEMPLATE_VERSION_ID,
        ) + extra,
        orderingKey = "information_request:$requestId",
    )

    private companion object
    {
        val EXCHANGE_ID = UUID.randomUUID().toString()
        val TEMPLATE_VERSION_ID = UUID.randomUUID().toString()
    }
}
