package com.docuhyphen.app.api.resource.informationrequest.notice

import com.docuhyphen.app.api.model.dto.InformationRequestNoticeAttemptDto
import com.docuhyphen.app.api.model.dto.InformationRequestNoticeHistoryDto
import com.docuhyphen.app.api.model.dto.InformationRequestNoticeSequenceAllocationDto
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.notice.InformationRequestNoticeView
import com.docuhyphen.app.api.resource.informationrequest.notice.operations.InformationRequestNoticeResourceOperations
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.notice.InformationRequestNoticeQueryService
import io.quarkus.security.ForbiddenException
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.sql.Timestamp
import java.time.Instant
import java.util.*

class InformationRequestNoticeResourceContractTest {
    private val requestId = UUID.randomUUID()
    private val access =
        RequestAccessContext(PrincipalRef.user(UUID.randomUUID()), AuthorizationContext(sessionRef = "manager"))
    private val notices: InformationRequestNoticeQueryService = mock()
    private val accessContextFactory: InformationRequestAccessContextFactory = mock {
        on { currentAuthenticated() } doReturn access
    }
    private val resource = InformationRequestNoticeResource(notices, accessContextFactory)

    @Test
    fun `notice history is a read-only sub-collection of one request`() {
        val operations = InformationRequestNoticeResourceOperations::class.java
        val list = operations.declaredMethods.single { it.name == "list" }

        assertEquals("/information-requests/{id}/notices", operations.getAnnotation(Path::class.java).value)
        assertArrayEquals(arrayOf(MediaType.APPLICATION_JSON), operations.getAnnotation(Produces::class.java).value)
        assertTrue(list.isAnnotationPresent(GET::class.java))
        assertEquals("id", list.parameters.single().getAnnotation(PathParam::class.java).value)
        assertEquals(1, operations.declaredMethods.size)
        assertTrue(InformationRequestNoticeResource::class.java.declaredMethods.none { method ->
            method.isAnnotationPresent(GET::class.java) || method.isAnnotationPresent(Path::class.java)
        })
    }

    @Test
    fun `each owed notice is answered with its rendered content, masked endpoint, attempts, and sequence values`() {
        val intent = InformationRequestNoticeIntent().apply {
            informationRequestId = requestId
            partyId = UUID.randomUUID()
            noticeKind = InformationRequestNoticeKind.RESPONSE_REMINDER
            createdAt = Timestamp.from(Instant.parse("2026-10-01T09:00:00Z"))
        }
        val notice = InformationRequestOutboundNotice().apply {
            noticeIntentId = intent.id
            informationRequestId = requestId
            partyId = intent.partyId
            recipientEndpoint = "respondent@example.test"
            endpointState = InformationRequestNoticeEndpointState.RESOLVED
            renderedSubject = "Reminder"
            renderedBody = "Reference SEQ-7"
            renderedContentHash = "rendered-hash"
            sourceContentHash = "source-hash"
            idempotencyKey = "notice-key"
            renderedAt = Timestamp.from(Instant.parse("2026-10-01T09:05:00Z"))
        }
        val attempt = InformationRequestNoticeDeliveryAttempt().apply {
            outboundNoticeId = notice.id
            informationRequestId = requestId
            attemptNumber = 2
            outcome = InformationRequestNoticeAttemptOutcome.FAILED
            failureCode = "MAILBOX_UNAVAILABLE"
            attemptedAt = Timestamp.from(Instant.parse("2026-10-01T09:06:00Z"))
        }
        val allocation = InformationRequestNoticeSequenceAllocation().apply {
            outboundNoticeId = notice.id
            sequenceKey = "REFERENCE"
            allocatedValue = 7
            renderedValue = "SEQ-7"
        }
        val pending = InformationRequestNoticeIntent().apply {
            informationRequestId = requestId
            partyId = UUID.randomUUID()
            noticeKind = InformationRequestNoticeKind.REQUIREMENTS_AMENDED
        }
        val delivered =
            InformationRequestNoticeView(intent, notice, listOf(attempt), InformationRequestNoticeDeliveryState.FAILED)
        val owed =
            InformationRequestNoticeView(pending, null, emptyList(), InformationRequestNoticeDeliveryState.PENDING)
        whenever(notices.notices(requestId, access)).thenReturn(listOf(delivered, owed))
        whenever(notices.allocationsOf(delivered)).thenReturn(listOf(allocation))
        whenever(notices.allocationsOf(owed)).thenReturn(emptyList())

        val response = resource.list(requestId.toString())

        assertEquals(Response.Status.OK.statusCode, response.status)
        @Suppress("UNCHECKED_CAST")
        val body = (response.entity as Array<InformationRequestNoticeHistoryDto>).toList()
        assertEquals(
            listOf(
                InformationRequestNoticeHistoryDto(
                    noticeIntentId = intent.id,
                    noticeKind = InformationRequestNoticeKind.RESPONSE_REMINDER,
                    partyId = intent.partyId,
                    deliveryState = InformationRequestNoticeDeliveryState.FAILED,
                    owedAt = intent.createdAt,
                    noticeId = notice.id,
                    channel = notice.channel,
                    maskedEndpoint = "r***@example.test",
                    endpointState = InformationRequestNoticeEndpointState.RESOLVED,
                    renderedSubject = "Reminder",
                    renderedBody = "Reference SEQ-7",
                    renderedContentHash = "rendered-hash",
                    sourceKind = notice.sourceKind,
                    sourceCommunicationId = null,
                    sourceContentHash = "source-hash",
                    renderedAt = notice.renderedAt,
                    attempts = listOf(
                        InformationRequestNoticeAttemptDto(
                            2,
                            InformationRequestNoticeAttemptOutcome.FAILED,
                            "MAILBOX_UNAVAILABLE",
                            attempt.attemptedAt
                        ),
                    ),
                    sequenceAllocations = listOf(InformationRequestNoticeSequenceAllocationDto("REFERENCE", "SEQ-7")),
                ),
                InformationRequestNoticeHistoryDto(
                    noticeIntentId = pending.id,
                    noticeKind = InformationRequestNoticeKind.REQUIREMENTS_AMENDED,
                    partyId = pending.partyId,
                    deliveryState = InformationRequestNoticeDeliveryState.PENDING,
                    owedAt = pending.createdAt,
                ),
            ),
            body,
        )
        verify(notices).notices(requestId, access)
    }

    @Test
    fun `a malformed request id is refused before the query runs`() {
        val response = resource.list("not-a-uuid")

        assertEquals(Response.Status.BAD_REQUEST.statusCode, response.status)
        assertEquals("Invalid information request id", (response.entity as ResponseError).errorMessage)
        verify(notices, never()).notices(any(), any())
    }

    @Test
    fun `refusals keep their status and reason`() {
        whenever(notices.notices(requestId, access))
            .thenThrow(ForbiddenException("denied"))
            .thenThrow(
                InformationRequestLifecycleException(
                    InformationRequestErrorCatalog.NOT_FOUND,
                    "Information Request not found"
                )
            )

        val forbidden = resource.list(requestId.toString())
        val missing = resource.list(requestId.toString())

        assertEquals(Response.Status.FORBIDDEN.statusCode, forbidden.status)
        assertEquals(InformationRequestErrorCatalog.FORBIDDEN, (forbidden.entity as ResponseError).reasonCode)
        assertEquals(Response.Status.NOT_FOUND.statusCode, missing.status)
        assertEquals(InformationRequestErrorCatalog.NOT_FOUND, (missing.entity as ResponseError).reasonCode)
    }
}
