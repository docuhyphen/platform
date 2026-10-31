package com.docuhyphen.app.api.resource.recordpreservation

import com.docuhyphen.app.api.exception.RecordPreservationErrorCatalog
import com.docuhyphen.app.api.exception.RecordPreservationException
import com.docuhyphen.app.api.exception.RecordPreservationNotFoundException
import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.dto.RecordPreservationHoldDto
import com.docuhyphen.app.api.model.dto.RecordRetentionScheduleDto
import com.docuhyphen.app.api.model.entity.RecordPreservationHold
import com.docuhyphen.app.api.model.entity.RecordPreservationHoldStatus
import com.docuhyphen.app.api.model.entity.RecordPreservationScope
import com.docuhyphen.app.api.model.recordpreservation.RecordPreservationHoldView
import com.docuhyphen.app.api.model.recordpreservation.RecordRetentionScheduleView
import com.docuhyphen.app.api.resource.model.*
import com.docuhyphen.app.api.resource.recordpreservation.operations.RecordDisposalResourceOperations
import com.docuhyphen.app.api.resource.recordpreservation.operations.RecordPreservationHoldResourceOperations
import com.docuhyphen.app.api.resource.recordpreservation.operations.RecordRetentionScheduleResourceOperations
import com.docuhyphen.app.api.service.command.CommandPreconditionException
import com.docuhyphen.app.api.service.command.CommandReceiptConflictException
import com.docuhyphen.app.api.service.recordpreservation.RecordPreservationAdministration
import io.quarkus.security.ForbiddenException
import jakarta.ws.rs.HeaderParam
import jakarta.ws.rs.PATCH
import jakarta.ws.rs.Path
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.util.*

class RecordPreservationResourceContractTest
{
    private val administration: RecordPreservationAdministration = mock()
    private val holds = RecordPreservationHoldResource(administration)
    private val schedules = RecordRetentionScheduleResource(administration)

    @Test
    fun `holds, schedules, and disposals are top-level collections and placing a hold creates it`()
    {
        val hold = hold()
        whenever(administration.placeHold(any(), any(), any(), any(), anyOrNull(), anyOrNull(), any()))
            .thenReturn(RecordPreservationHoldView(hold, emptyList()))

        val response = holds.place(
            PlaceRecordPreservationHoldRequest(
                hold.resourceType,
                hold.resourceId,
                RecordPreservationScope.DESCENDANTS_AND_REFERENCES,
                "records review"
            ),
            "place-1",
        )

        assertEquals(
            "/record-preservation-holds",
            RecordPreservationHoldResourceOperations::class.java.getAnnotation(Path::class.java).value
        )
        assertEquals(
            "/record-retention-schedules",
            RecordRetentionScheduleResourceOperations::class.java.getAnnotation(Path::class.java).value
        )
        assertEquals(
            "/record-disposals",
            RecordDisposalResourceOperations::class.java.getAnnotation(Path::class.java).value
        )
        listOf(
            RecordPreservationHoldResource::class.java,
            RecordRetentionScheduleResource::class.java,
            RecordDisposalResource::class.java
        )
            .forEach { assertNull(it.getAnnotation(Path::class.java), it.simpleName) }
        assertEquals(201, response.status)
        assertEquals(
            RecordPreservationScope.DESCENDANTS_AND_REFERENCES,
            (response.entity as RecordPreservationHoldDto).scope
        )
        verify(administration).placeHold(
            eq("INFORMATION_REQUEST"),
            eq(hold.resourceId),
            eq(RecordPreservationScope.DESCENDANTS_AND_REFERENCES),
            eq("records review"),
            anyOrNull(),
            anyOrNull(),
            eq("place-1"),
        )
    }

    @Test
    fun `every hold and schedule change names its idempotency key, and every change to an existing one its If-Match`() {
        val place = RecordPreservationHoldResourceOperations::class.java.getMethod(
            "place",
            PlaceRecordPreservationHoldRequest::class.java,
            String::class.java
        )
        val conditional = listOf(
            RecordPreservationHoldResourceOperations::class.java.getMethod(
                "changeScope",
                String::class.java,
                ChangeRecordPreservationHoldScopeRequest::class.java,
                String::class.java,
                String::class.java,
            ),
            RecordPreservationHoldResourceOperations::class.java.getMethod(
                "release",
                String::class.java,
                ReleaseRecordPreservationHoldRequest::class.java,
                String::class.java,
                String::class.java,
            ),
            RecordRetentionScheduleResourceOperations::class.java.getMethod(
                "publish",
                String::class.java,
                PublishRecordRetentionScheduleRequest::class.java,
                String::class.java,
                String::class.java,
            ),
        )
        val headersOf = { method: java.lang.reflect.Method ->
            method.parameterAnnotations.flatMap { it.toList() }.filterIsInstance<HeaderParam>().map { it.value }
        }

        assertEquals(listOf("Idempotency-Key"), headersOf(place))
        conditional.forEach { method ->
            assertEquals(
                listOf("If-Match", "Idempotency-Key"),
                headersOf(method),
                method.name
            )
        }
        val scope = conditional.first()
        assertTrue(scope.isAnnotationPresent(PATCH::class.java))
        assertEquals("/{holdId}/scope", scope.getAnnotation(Path::class.java).value)
    }

    @Test
    fun `a change without If-Match or with a stale one is refused with the current ETag and never applied`() {
        val holdId = UUID.randomUUID()
        val current = "\"$holdId:4\""
        whenever(administration.changeHoldScope(eq(holdId), any(), any(), any(), eq("scope-unstated")))
            .thenThrow(CommandPreconditionException.required(current))
        whenever(administration.releaseHold(eq(holdId), any(), any(), eq("release-stale")))
            .thenThrow(CommandPreconditionException.stale(current))
        whenever(administration.publishSchedule(any(), any(), anyOrNull(), any(), eq("publish-stale")))
            .thenThrow(CommandPreconditionException.stale("\"schedule:1\""))

        val unstated = holds.changeScope(
            holdId.toString(),
            ChangeRecordPreservationHoldScopeRequest(RecordPreservationScope.RESOURCE, "narrowed"),
            null,
            "scope-unstated",
        )
        val stale = holds.release(
            holdId.toString(),
            ReleaseRecordPreservationHoldRequest("done"),
            "\"$holdId:3\"",
            "release-stale"
        )
        val staleSchedule = schedules.publish(
            "INFORMATION_REQUEST",
            PublishRecordRetentionScheduleRequest(30, 90),
            "\"schedule:0\"",
            "publish-stale",
        )

        assertEquals(428, unstated.status)
        assertEquals("COMMAND_PRECONDITION_REQUIRED", (unstated.entity as ResponseError).reasonCode)
        assertEquals(412, stale.status)
        assertEquals("COMMAND_PRECONDITION_STALE", (stale.entity as ResponseError).reasonCode)
        assertEquals(current, stale.getHeaderString("ETag"))
        assertEquals(412, staleSchedule.status)
        verify(administration).changeHoldScope(
            eq(holdId),
            any(),
            any(),
            eq(CommandPrecondition.Absent),
            eq("scope-unstated")
        )
    }

    @Test
    fun `hold and schedule reads and changes answer the ETag of what they return`() {
        val hold = hold().apply { holdRevision = 2 }
        whenever(administration.hold(hold.id)).thenReturn(RecordPreservationHoldView(hold, emptyList()))
        whenever(administration.changeHoldScope(eq(hold.id), any(), any(), any(), any()))
            .thenReturn(RecordPreservationHoldView(hold, emptyList()))
        whenever(administration.schedule(any())).thenReturn(
            RecordRetentionScheduleView(
                null,
                emptyList(),
                "\"schedule:0\""
            )
        )

        val read = holds.get(hold.id.toString())
        val changed = holds.changeScope(
            hold.id.toString(),
            ChangeRecordPreservationHoldScopeRequest(RecordPreservationScope.RESOURCE, "narrowed"),
            "\"${hold.id}:1\"",
            "scope-1",
        )
        val schedule = schedules.get("INFORMATION_REQUEST")

        val etag = "\"${hold.id}:2\""
        assertEquals(etag, read.getHeaderString("ETag"))
        assertEquals(etag, (read.entity as RecordPreservationHoldDto).holdETag)
        assertEquals(etag, changed.getHeaderString("ETag"))
        assertEquals("\"schedule:0\"", schedule.getHeaderString("ETag"))
        assertEquals("\"schedule:0\"", (schedule.entity as RecordRetentionScheduleDto).scheduleETag)
        verify(administration).changeHoldScope(
            eq(hold.id),
            any(),
            any(),
            eq(CommandPrecondition.ExpectedRevision("\"${hold.id}:1\"")),
            eq("scope-1"),
        )
    }

    @Test
    fun `a missing body, a missing idempotency key, an unknown status, or a malformed hold id is a bad request that never reaches the service`()
    {
        val holdId = UUID.randomUUID().toString()
        assertEquals(400, holds.place(null, "place-1").status)
        assertEquals(
            400,
            holds.place(
                PlaceRecordPreservationHoldRequest(
                    "EXCHANGE",
                    "record-1",
                    RecordPreservationScope.RESOURCE,
                    "review"
                ), null
            ).status
        )
        assertEquals(400, holds.release(holdId, null, "\"etag\"", "release-1").status)
        assertEquals(400, holds.release(holdId, ReleaseRecordPreservationHoldRequest("done"), "\"etag\"", " ").status)
        assertEquals(
            400,
            holds.release("not-a-hold", ReleaseRecordPreservationHoldRequest("done"), "\"etag\"", "release-1").status
        )
        assertEquals(
            400,
            holds.changeScope(
                holdId,
                ChangeRecordPreservationHoldScopeRequest(RecordPreservationScope.RESOURCE, "narrowed"),
                "\"etag\"",
                null
            ).status
        )
        assertEquals(400, holds.list(listOf("pending")).status)
        assertEquals(400, schedules.publish("INFORMATION_REQUEST", null, "\"etag\"", "publish-1").status)
        assertEquals(
            400,
            schedules.publish(
                "INFORMATION_REQUEST",
                PublishRecordRetentionScheduleRequest(30, 90),
                "\"etag\"",
                null
            ).status
        )
        verifyNoInteractions(administration)
    }

    @Test
    fun `changing a hold's scope returns the changed hold`() {
        val hold = hold().apply {
            scope = RecordPreservationScope.RESOURCE
            holdRevision = 2
        }
        whenever(
            administration.changeHoldScope(
                eq(hold.id),
                eq(RecordPreservationScope.RESOURCE),
                eq("narrowed"),
                any(),
                eq("scope-1")
            )
        )
            .thenReturn(RecordPreservationHoldView(hold, emptyList()))

        val response = holds.changeScope(
            hold.id.toString(),
            ChangeRecordPreservationHoldScopeRequest(RecordPreservationScope.RESOURCE, "narrowed"),
            "\"${hold.id}:1\"",
            "scope-1",
        )

        assertEquals(200, response.status)
        assertEquals(RecordPreservationScope.RESOURCE, (response.entity as RecordPreservationHoldDto).scope)
    }

    @Test
    fun `refusals keep their stable reason, an unknown hold is not found, a reused key is a conflict, and a refused caller is forbidden`()
    {
        val released = UUID.randomUUID()
        val unknown = UUID.randomUUID()
        val reused = UUID.randomUUID()
        whenever(administration.releaseHold(eq(released), any(), any(), any()))
            .thenThrow(
                RecordPreservationException(
                    RecordPreservationErrorCatalog.HOLD_RELEASED,
                    "A released hold does not change"
                )
            )
        whenever(
            administration.releaseHold(
                eq(unknown),
                any(),
                any(),
                any()
            )
        ).thenThrow(RecordPreservationNotFoundException("Hold not found"))
        whenever(
            administration.releaseHold(
                eq(reused),
                any(),
                any(),
                any()
            )
        ).thenThrow(CommandReceiptConflictException())
        whenever(administration.holds(setOf(RecordPreservationHoldStatus.ACTIVE))).thenThrow(ForbiddenException("denied"))
        whenever(administration.schedule(any())).thenReturn(
            RecordRetentionScheduleView(
                null,
                emptyList(),
                "\"schedule:0\""
            )
        )

        val conflict =
            holds.release(released.toString(), ReleaseRecordPreservationHoldRequest("done"), "\"etag\"", "release-1")
        assertEquals(409, conflict.status)
        assertEquals(RecordPreservationErrorCatalog.HOLD_RELEASED, (conflict.entity as ResponseError).reasonCode)
        assertEquals(
            404,
            holds.release(
                unknown.toString(),
                ReleaseRecordPreservationHoldRequest("done"),
                "\"etag\"",
                "release-1"
            ).status
        )
        val receiptConflict =
            holds.release(reused.toString(), ReleaseRecordPreservationHoldRequest("done"), "\"etag\"", "release-1")
        assertEquals(409, receiptConflict.status)
        assertEquals("COMMAND_RECEIPT_FINGERPRINT_CONFLICT", (receiptConflict.entity as ResponseError).reasonCode)
        assertEquals(403, holds.list(listOf("active")).status)
        val schedule = schedules.get("information_request")
        assertEquals(200, schedule.status)
        assertEquals("INFORMATION_REQUEST", (schedule.entity as RecordRetentionScheduleDto).resourceType)
        verify(administration, never()).publishSchedule(any(), any(), anyOrNull(), any(), any())
    }

    private fun hold() = RecordPreservationHold().apply {
        resourceType = "INFORMATION_REQUEST"
        resourceId = UUID.randomUUID().toString()
        reason = "records review"
        scope = RecordPreservationScope.DESCENDANTS_AND_REFERENCES
        placedByPrincipalId = UUID.randomUUID()
    }
}
