package com.docuhyphen.app.api.service.informationrequest.workflow

import com.docuhyphen.app.api.exception.SubscriptionDenialException
import com.docuhyphen.app.api.exception.WorkflowStartRefusedException
import com.docuhyphen.app.api.model.entity.InformationRequestOwnerType
import com.docuhyphen.app.api.model.informationrequest.access.InformationRequestOwnerRef
import com.docuhyphen.app.api.model.notification.DomainEventConsumptionOutcome
import com.docuhyphen.app.api.model.workflow.TriggerRequest
import com.docuhyphen.app.api.model.workflow.TriggerResult
import com.docuhyphen.app.api.service.informationrequest.InformationRequestQueryService
import com.docuhyphen.app.api.service.notification.DomainEvent
import com.docuhyphen.app.api.service.subscription.*
import com.docuhyphen.app.api.service.workflow.WorkflowEngineService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import java.util.*

class InformationRequestWorkflowTriggerConsumerTest
{
    private val engine = mock<WorkflowEngineService>()
    private val queryService = mock<InformationRequestQueryService>()
    private val consumer = InformationRequestWorkflowTriggerConsumer(engine, queryService)
    private val requestId = UUID.randomUUID()
    private val organizationId = UUID.randomUUID()
    private val userId = UUID.randomUUID()

    @Test
    fun `only registered request triggers about a request subject are consumed`()
    {
        assertTrue(consumer.handles(event("information_request.request.submit")))
        assertTrue(consumer.handles(event("information_request.request.overdue")))
        assertFalse(consumer.handles(event("information_request.review.assign")))
        assertFalse(
            consumer.handles(
                event("information_request.request.submit").copy(
                    subject = DomainEvent.SubjectRef(
                        "EXCHANGE",
                        requestId.toString()
                    )
                )
            )
        )
    }

    @Test
    fun `a cancelled, superseded or expired request ends the workflows already running on it before starting new ones`() {
        listOf(
            "information_request.request.cancel",
            "information_request.request.supersede",
            "information_request.request.expire"
        )
            .forEach { eventName ->
                clearInvocations(engine)

                consumer.consume(event(eventName))

                val order = inOrder(engine)
                order.verify(engine).cancelActiveForSubject("INFORMATION_REQUEST", requestId, eventName)
                order.verify(engine).trigger(any())
            }
    }

    @Test
    fun `a request that goes on leaves its running workflows alone`() {
        consumer.consume(event("information_request.request.submit"))

        verify(engine, never()).cancelActiveForSubject(any(), any(), anyOrNull())
    }

    @Test
    fun `a submission starts matching workflows with only the safe fields its subject schema names`()
    {
        val packageId = UUID.randomUUID()
        whenever(engine.trigger(any())).thenReturn(
            TriggerResult(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                emptyList()
            )
        )

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

        val result = consumer.consume(
            event(
                "information_request.request.close",
                mapOf("satisfiedByPackageId" to packageId.toString())
            )
        )

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

    @Test
    fun `a definition that can never start is a skipped consumption naming the definition and reason`() {
        val definitionId = UUID.randomUUID()
        whenever(engine.trigger(any())).thenThrow(WorkflowStartRefusedException(definitionId, "SPEC_UNREADABLE"))

        val result = consumer.consume(event("information_request.request.issue"))

        assertEquals(DomainEventConsumptionOutcome.SKIPPED, result.outcome)
        assertEquals("WORKFLOW_START_REFUSED:$definitionId:SPEC_UNREADABLE", result.detail)
    }

    @Test
    fun `a transient engine failure is not swallowed so the delivery is retried`() {
        whenever(engine.trigger(any())).thenThrow(IllegalStateException("connection lost"))

        assertThrows<IllegalStateException> { consumer.consume(event("information_request.request.issue")) }
    }

    @Test
    fun `a personally owned request names its owner so the owner's own definitions are considered`() {
        val ownerId = UUID.randomUUID()
        whenever(queryService.ownerOf(requestId)).thenReturn(
            InformationRequestOwnerRef(
                InformationRequestOwnerType.USER,
                ownerId
            )
        )
        whenever(engine.trigger(any())).thenReturn(null)

        consumer.consume(event("information_request.request.issue").copy(organizationId = null))

        val captor = argumentCaptor<TriggerRequest>()
        verify(engine).trigger(captor.capture())
        assertEquals(null, captor.firstValue.organizationId)
        assertEquals(ownerId, captor.firstValue.ownerAppUserId)
    }

    @Test
    fun `an organization owned request names no personal owner`() {
        whenever(queryService.ownerOf(requestId)).thenReturn(
            InformationRequestOwnerRef(
                InformationRequestOwnerType.ORGANIZATION,
                organizationId
            )
        )
        whenever(engine.trigger(any())).thenReturn(null)

        consumer.consume(event("information_request.request.issue"))

        val captor = argumentCaptor<TriggerRequest>()
        verify(engine).trigger(captor.capture())
        assertEquals(organizationId, captor.firstValue.organizationId)
        assertEquals(null, captor.firstValue.ownerAppUserId)
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
