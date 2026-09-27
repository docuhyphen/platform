package com.docuhyphen.app.api.resource.recordpreservation

import com.docuhyphen.app.api.exception.RecordPreservationErrorCatalog
import com.docuhyphen.app.api.exception.RecordPreservationException
import com.docuhyphen.app.api.exception.RecordPreservationNotFoundException
import com.docuhyphen.app.api.model.dto.RecordPreservationHoldDto
import com.docuhyphen.app.api.model.dto.RecordRetentionScheduleDto
import com.docuhyphen.app.api.model.entity.RecordPreservationHold
import com.docuhyphen.app.api.model.entity.RecordPreservationHoldStatus
import com.docuhyphen.app.api.model.entity.RecordPreservationScope
import com.docuhyphen.app.api.model.recordpreservation.RecordPreservationHoldView
import com.docuhyphen.app.api.model.recordpreservation.RecordRetentionScheduleView
import com.docuhyphen.app.api.resource.model.PlaceRecordPreservationHoldRequest
import com.docuhyphen.app.api.resource.model.ReleaseRecordPreservationHoldRequest
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.recordpreservation.RecordPreservationAdministration
import io.quarkus.security.ForbiddenException
import jakarta.ws.rs.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.util.UUID

class RecordPreservationResourceContractTest
{
    private val administration: RecordPreservationAdministration = mock()
    private val holds = RecordPreservationHoldResource(administration)
    private val schedules = RecordRetentionScheduleResource(administration)

    @Test
    fun `holds, schedules, and disposals are top-level collections and placing a hold creates it`()
    {
        val hold = RecordPreservationHold().apply {
            resourceType = "INFORMATION_REQUEST"
            resourceId = UUID.randomUUID().toString()
            reason = "records review"
            scope = RecordPreservationScope.DESCENDANTS_AND_REFERENCES
            placedByPrincipalId = UUID.randomUUID()
        }
        whenever(administration.placeHold(any(), any(), any(), any(), anyOrNull(), anyOrNull())).thenReturn(RecordPreservationHoldView(hold, emptyList()))

        val response = holds.place(
            PlaceRecordPreservationHoldRequest(hold.resourceType, hold.resourceId, RecordPreservationScope.DESCENDANTS_AND_REFERENCES, "records review"),
        )

        assertEquals("/record-preservation-holds", RecordPreservationHoldResource::class.java.getAnnotation(Path::class.java).value)
        assertEquals("/record-retention-schedules", RecordRetentionScheduleResource::class.java.getAnnotation(Path::class.java).value)
        assertEquals("/record-disposals", RecordDisposalResource::class.java.getAnnotation(Path::class.java).value)
        assertEquals(201, response.status)
        assertEquals(RecordPreservationScope.DESCENDANTS_AND_REFERENCES, (response.entity as RecordPreservationHoldDto).scope)
        verify(administration).placeHold(eq("INFORMATION_REQUEST"), eq(hold.resourceId), eq(RecordPreservationScope.DESCENDANTS_AND_REFERENCES), eq("records review"), anyOrNull(), anyOrNull())
    }

    @Test
    fun `a missing body, an unknown status, or a malformed hold id is a bad request that never reaches the service`()
    {
        assertEquals(400, holds.place(null).status)
        assertEquals(400, holds.release(UUID.randomUUID().toString(), null).status)
        assertEquals(400, holds.release("not-a-hold", ReleaseRecordPreservationHoldRequest("done")).status)
        assertEquals(400, holds.list(listOf("pending")).status)
        assertEquals(400, schedules.publish("INFORMATION_REQUEST", null).status)
        verifyNoInteractions(administration)
    }

    @Test
    fun `refusals keep their stable reason, an unknown hold is not found, and a refused caller is forbidden`()
    {
        val released = UUID.randomUUID()
        val unknown = UUID.randomUUID()
        whenever(administration.releaseHold(eq(released), any()))
            .thenThrow(RecordPreservationException(RecordPreservationErrorCatalog.HOLD_RELEASED, "A released hold does not change"))
        whenever(administration.releaseHold(eq(unknown), any())).thenThrow(RecordPreservationNotFoundException("Hold not found"))
        whenever(administration.holds(setOf(RecordPreservationHoldStatus.ACTIVE))).thenThrow(ForbiddenException("denied"))
        whenever(administration.schedule(any())).thenReturn(RecordRetentionScheduleView(null, emptyList()))

        val conflict = holds.release(released.toString(), ReleaseRecordPreservationHoldRequest("done"))
        assertEquals(409, conflict.status)
        assertEquals(RecordPreservationErrorCatalog.HOLD_RELEASED, (conflict.entity as ResponseError).reasonCode)
        assertEquals(404, holds.release(unknown.toString(), ReleaseRecordPreservationHoldRequest("done")).status)
        assertEquals(403, holds.list(listOf("active")).status)
        val schedule = schedules.get("information_request")
        assertEquals(200, schedule.status)
        assertEquals("INFORMATION_REQUEST", (schedule.entity as RecordRetentionScheduleDto).resourceType)
        verify(administration, never()).publishSchedule(any(), any(), anyOrNull())
    }
}
