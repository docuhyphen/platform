package com.docuhyphen.app.api.resource.informationrequest

import com.docuhyphen.app.api.model.dto.InformationRequestOperationsPageDto
import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestNoticeDeliveryState
import com.docuhyphen.app.api.model.informationrequest.InformationRequestOperationsException
import com.docuhyphen.app.api.model.informationrequest.InformationRequestOperationsFilter
import com.docuhyphen.app.api.model.informationrequest.InformationRequestOperationsPage
import com.docuhyphen.app.api.model.informationrequest.InformationRequestOperationsRow
import com.docuhyphen.app.api.model.informationrequest.InformationRequestSlaStanding
import com.docuhyphen.app.api.model.informationrequest.InformationRequestSlaStatus
import com.docuhyphen.app.api.service.informationrequest.InformationRequestOperationsService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestState
import io.quarkus.security.ForbiddenException
import jakarta.ws.rs.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.UUID

class InformationRequestOperationsResourceContractTest
{
    private val operations: InformationRequestOperationsService = mock()
    private val resource = InformationRequestOperationsResource(operations)

    @Test
    fun `the queue is a top-level collection whose query becomes the service filter`()
    {
        val exchangeId = UUID.randomUUID()
        val request = InformationRequest().apply {
            this.exchangeId = exchangeId
            templateVersionId = UUID.randomUUID()
            state = InformationRequestState.ISSUED
        }
        val due = Instant.parse("2026-09-26T12:00:00Z")
        whenever(operations.queue(any())).thenReturn(
            InformationRequestOperationsPage(
                listOf(
                    InformationRequestOperationsRow(
                        request = request,
                        ageSeconds = 120,
                        clockCount = 1,
                        standing = InformationRequestSlaStanding(InformationRequestSlaStatus.OVERDUE, due, 2, 0),
                        noticeCounts = mapOf(InformationRequestNoticeDeliveryState.UNDELIVERABLE to 1),
                        exceptionCounts = mapOf(InformationRequestOperationsException.NOTICE_UNDELIVERABLE to 1),
                    ),
                ),
                total = 7,
                limit = 1,
                offset = 3,
            ),
        )

        val response = resource.queue(listOf("issued", "IN_PROGRESS"), exchangeId.toString(), listOf("overdue"), listOf("NOTICE_UNDELIVERABLE"), true, 1, 3)

        assertEquals("/information-request-operations", InformationRequestOperationsResource::class.java.getAnnotation(Path::class.java).value)
        assertEquals(200, response.status)
        val body = response.entity as InformationRequestOperationsPageDto
        assertEquals(7, body.total)
        assertEquals(InformationRequestSlaStatus.OVERDUE, body.items.single().slaStatus)
        assertEquals(due, body.items.single().nearestDueAt?.toInstant())
        assertEquals(mapOf(InformationRequestOperationsException.NOTICE_UNDELIVERABLE to 1), body.items.single().exceptionCounts)
        val filter = argumentCaptor<InformationRequestOperationsFilter>().also { verify(operations).queue(it.capture()) }.firstValue
        assertEquals(setOf(InformationRequestState.ISSUED, InformationRequestState.IN_PROGRESS), filter.states)
        assertEquals(exchangeId, filter.exchangeId)
        assertEquals(setOf(InformationRequestSlaStatus.OVERDUE), filter.slaStatuses)
        assertEquals(setOf(InformationRequestOperationsException.NOTICE_UNDELIVERABLE), filter.exceptions)
        assertEquals(true, filter.exceptionsOnly)
        assertEquals(1, filter.limit)
        assertEquals(3, filter.offset)
    }

    @Test
    fun `an unknown filter value or an out of range page is a bad request and a refused caller is forbidden`()
    {
        assertEquals(400, resource.queue(listOf("OPEN"), null, null, null, null, null, null).status)
        assertEquals(400, resource.queue(null, null, listOf("LATE"), null, null, null, null).status)
        assertEquals(400, resource.queue(null, "not-an-id", null, null, null, null, null).status)
        assertEquals(400, resource.queue(null, null, null, null, null, 0, null).status)
        assertEquals(400, resource.queue(null, null, null, null, null, 201, null).status)
        assertEquals(400, resource.queue(null, null, null, null, null, null, -1).status)
        verify(operations, never()).queue(any())

        whenever(operations.queue(any())).thenThrow(ForbiddenException("denied"))
        assertEquals(403, resource.queue(null, null, null, null, null, null, null).status)
    }
}
