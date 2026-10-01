package com.docuhyphen.app.api.resource.informationrequest.oversight

import com.docuhyphen.app.api.model.dto.InformationRequestOperationsPageDto
import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestNoticeDeliveryState
import com.docuhyphen.app.api.model.entity.InformationRequestShareRoleKey
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestState
import com.docuhyphen.app.api.model.informationrequest.oversight.InformationRequestOperationsAssignee
import com.docuhyphen.app.api.model.informationrequest.oversight.InformationRequestOperationsException
import com.docuhyphen.app.api.model.informationrequest.oversight.InformationRequestOperationsFilter
import com.docuhyphen.app.api.model.informationrequest.oversight.InformationRequestOperationsPage
import com.docuhyphen.app.api.model.informationrequest.oversight.InformationRequestOperationsRow
import com.docuhyphen.app.api.model.informationrequest.oversight.InformationRequestSlaStanding
import com.docuhyphen.app.api.model.informationrequest.oversight.InformationRequestSlaStatus
import com.docuhyphen.app.api.resource.informationrequest.oversight.operations.InformationRequestOperationsResourceOperations
import com.docuhyphen.app.api.service.informationrequest.oversight.InformationRequestOperationsService
import io.quarkus.security.ForbiddenException
import jakarta.ws.rs.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.time.Instant
import java.util.*

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
        val assigneeId = UUID.randomUUID()
        whenever(operations.queue(any())).thenReturn(
            InformationRequestOperationsPage(
                listOf(
                    InformationRequestOperationsRow(
                        request = request,
                        title = "Collection pattern",
                        assignees = listOf(
                            InformationRequestOperationsAssignee(
                                InformationRequestShareRoleKey.CONTRIBUTOR,
                                PrincipalKind.USER,
                                assigneeId,
                                "member@process.test",
                            ),
                        ),
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

        val response = resource.queue(
            listOf("issued", "IN_PROGRESS"), exchangeId.toString(), "  collection ", assigneeId.toString(),
            listOf("overdue"), listOf("NOTICE_UNDELIVERABLE"), true, 1, 3,
        )

        assertEquals("/information-request-operations", InformationRequestOperationsResourceOperations::class.java.getAnnotation(Path::class.java).value)
        assertEquals(200, response.status)
        val body = response.entity as InformationRequestOperationsPageDto
        assertEquals(7, body.total)
        assertEquals(InformationRequestSlaStatus.OVERDUE, body.items.single().slaStatus)
        assertEquals(due, body.items.single().nearestDueAt?.toInstant())
        assertEquals(mapOf(InformationRequestOperationsException.NOTICE_UNDELIVERABLE to 1), body.items.single().exceptionCounts)
        assertEquals("Collection pattern", body.items.single().title)
        val assignee = body.items.single().assignees.single()
        assertEquals(InformationRequestShareRoleKey.CONTRIBUTOR, assignee.roleKey)
        assertEquals(assigneeId, assignee.principalId)
        assertEquals("member@process.test", assignee.label)
        val filter = argumentCaptor<InformationRequestOperationsFilter>().also { verify(operations).queue(it.capture()) }.firstValue
        assertEquals(setOf(InformationRequestState.ISSUED, InformationRequestState.IN_PROGRESS), filter.states)
        assertEquals(exchangeId, filter.exchangeId)
        assertEquals("collection", filter.search)
        assertEquals(assigneeId, filter.assigneeId)
        assertEquals(setOf(InformationRequestSlaStatus.OVERDUE), filter.slaStatuses)
        assertEquals(setOf(InformationRequestOperationsException.NOTICE_UNDELIVERABLE), filter.exceptions)
        assertEquals(true, filter.exceptionsOnly)
        assertEquals(1, filter.limit)
        assertEquals(3, filter.offset)
    }

    @Test
    fun `an unknown filter value or an out of range page is a bad request and a refused caller is forbidden`()
    {
        assertEquals(400, resource.queue(listOf("OPEN"), null, null, null, null, null, null, null, null).status)
        assertEquals(400, resource.queue(null, null, null, null, listOf("LATE"), null, null, null, null).status)
        assertEquals(400, resource.queue(null, "not-an-id", null, null, null, null, null, null, null).status)
        assertEquals(400, resource.queue(null, null, null, "not-an-id", null, null, null, null, null).status)
        assertEquals(400, resource.queue(null, null, null, null, null, null, null, 0, null).status)
        assertEquals(400, resource.queue(null, null, null, null, null, null, null, 201, null).status)
        assertEquals(400, resource.queue(null, null, null, null, null, null, null, null, -1).status)
        verify(operations, never()).queue(any())

        whenever(operations.queue(any())).thenThrow(ForbiddenException("denied"))
        assertEquals(403, resource.queue(null, null, null, null, null, null, null, null, null).status)
    }
}
