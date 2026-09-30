package com.docuhyphen.app.api.resource.informationrequest

import com.docuhyphen.app.api.exception.InformationRequestCommandRequestException
import com.docuhyphen.app.api.model.dto.InformationRequestReminderResultDto
import com.docuhyphen.app.api.model.informationrequest.InformationRequestReminderResult
import com.docuhyphen.app.api.model.informationrequest.SendInformationRequestRemindersCommand
import com.docuhyphen.app.api.resource.model.SendInformationRequestRemindersRequest
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.InformationRequestReminderService
import io.quarkus.security.ForbiddenException
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.core.Response
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

class InformationRequestReminderResourceContractTest
{
    private val reminderService = mock<InformationRequestReminderService>()
    private val resource = InformationRequestReminderResource(reminderService)

    @Test
    fun `reminders are a top-level collection whose POST needs an Idempotency-Key and answers each request's notices`()
    {
        val requestId = UUID.randomUUID()
        whenever(reminderService.send(any())).thenReturn(listOf(InformationRequestReminderResult(requestId, 2)))

        val missingKey = resource.send(SendInformationRequestRemindersRequest(listOf(requestId)), null)
        val sent = resource.send(SendInformationRequestRemindersRequest(listOf(requestId)), "remind-1")

        assertEquals("/information-request-reminders", InformationRequestReminderResource::class.java.getAnnotation(Path::class.java).value)
        assertTrue(InformationRequestReminderResource::class.java.declaredMethods.single { it.name == "send" }.isAnnotationPresent(POST::class.java))
        assertEquals(Response.Status.BAD_REQUEST.statusCode, missingKey.status)
        assertEquals(Response.Status.CREATED.statusCode, sent.status)
        @Suppress("UNCHECKED_CAST")
        assertEquals(listOf(InformationRequestReminderResultDto(requestId, 2)), sent.entity as List<InformationRequestReminderResultDto>)
        verify(reminderService).send(SendInformationRequestRemindersCommand(listOf(requestId), "remind-1"))
    }

    @Test
    fun `a request still cooling down is answered with the time it can be reminded again`()
    {
        val requestId = UUID.randomUUID()
        whenever(reminderService.send(any())).thenReturn(
            listOf(InformationRequestReminderResult(requestId, 0, java.time.Instant.parse("2026-10-01T11:00:00Z"))),
        )

        val sent = resource.send(SendInformationRequestRemindersRequest(listOf(requestId)), "remind-2")

        @Suppress("UNCHECKED_CAST")
        assertEquals(
            listOf(InformationRequestReminderResultDto(requestId, 0, "2026-10-01T11:00:00Z")),
            sent.entity as List<InformationRequestReminderResultDto>,
        )
    }

    @Test
    fun `a refused reminder keeps its status`()
    {
        whenever(reminderService.send(any()))
            .thenThrow(ForbiddenException("denied"))
            .thenThrow(InformationRequestLifecycleException(InformationRequestErrorCatalog.NOT_FOUND, "Information Request not found"))
            .thenThrow(InformationRequestLifecycleException(InformationRequestErrorCatalog.STATE_INVALID, "not open"))
            .thenThrow(InformationRequestCommandRequestException("Name at least one Information Request"))

        val statuses = List(4) { resource.send(SendInformationRequestRemindersRequest(listOf(UUID.randomUUID())), "remind-$it").status }

        assertEquals(listOf(403, 404, 409, 400), statuses)
    }
}
